package com.hiddify.hiddify.nativeprivacy

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class NativePrivacySetupTest {
    private fun configured(original: Map<String, Any> = emptyMap()): Map<String, Any> =
        original + NativePrivacySetup.configure(original)

    @Test fun defaultSetupCanBeRestoredAndDoesNotImplicitlyEnableRootOrFilters() {
        val applied = configured()
        assertTrue(NativePrivacySetup.isConfigured(applied))
        assertTrue(NativePrivacySetup.canRestore(applied))
        assertEquals(false, applied["flutter.privacy-use-root"])
        assertFalse(applied.filterKeys { "anonymization" in it || "modern-protocols" in it }.values.any { it == true })
        val restored = applied + NativePrivacySetup.restore(applied)
        assertFalse(NativePrivacySetup.canRestore(restored))
        assertEquals(9000L, restored["flutter.mtu"])
        assertEquals(false, restored["flutter.privacy-public-dns"])
        assertFalse(NativePrivacySetup.isConfigured(restored))
    }

    @Test fun capturesDartFieldNamesAndRestoresCustomValues() {
        val original = mapOf<String, Any>(
            "flutter.service-mode" to "proxy", "flutter.ipv6-mode" to "prefer_ipv6",
            "flutter.mtu" to 1600L, "flutter.auto_reconnect" to false,
            "flutter.handbook-routing" to true, "flutter.privacy-routing-mode" to "off",
            "flutter.privacy-direct-packages" to "manual:com.Example.App",
            "flutter.privacy-proxy-domains" to "example.org", "flutter.privacy-use-root" to true,
            "flutter.privacy-russian-network-bypass" to false,
        )
        val applied = configured(original)
        val snapshot = JsonParser.parseString(applied[NativePrivacySetup.BACKUP_KEY] as String).asJsonObject
        assertEquals("proxy", snapshot["serviceMode"].asString)
        assertEquals(1600L, snapshot["mtu"].asLong)
        assertEquals("manual:com.Example.App", snapshot["customDirectPackages"].asString)
        assertFalse(snapshot.has("flutter.service-mode"))
        val restored = applied + NativePrivacySetup.restore(applied)
        original.forEach { (key, value) -> assertEquals(key, value, restored[key]) }
    }

    @Test fun repeatedSetupKeepsFirstBackupEvenIfOtherValuesChanged() {
        val first = configured(mapOf("flutter.mtu" to 1800L))
        val repeated = configured(first + ("flutter.privacy-public-dns" to false))
        assertEquals(first[NativePrivacySetup.BACKUP_KEY], repeated[NativePrivacySetup.BACKUP_KEY])
        assertEquals(1800L, NativePrivacySetup.restore(repeated)["flutter.mtu"])
    }

    @Test fun enrichesLegacyBackupWithoutReplacingItsOriginalValues() {
        val backup = """{"serviceMode":"vpn","ipv6Mode":"prefer_ipv4","mtu":1500,"routingMode":"proxy-selected","autoReconnect":false,"publicDns":true} """
        val applied = configured(mapOf(NativePrivacySetup.BACKUP_KEY to backup))
        val enriched = JsonParser.parseString(applied[NativePrivacySetup.BACKUP_KEY] as String).asJsonObject
        assertEquals("proxy-selected", enriched["routingMode"].asString)
        assertEquals(1500L, enriched["mtu"].asLong)
        assertTrue(enriched.has("perAppMode"))
        val changes = NativePrivacySetup.restore(applied)
        assertEquals("proxy-selected", changes["flutter.privacy-routing-mode"])
        assertEquals(false, changes["flutter.auto_reconnect"])
        assertEquals(1500L, changes["flutter.mtu"])
    }

    @Test fun coreCacheAndFlutterValuesAreUpdatedTogetherWithoutLosingUnrelatedOptions() {
        val original = mapOf<String, Any>(NativePrivacySetup.CORE_KEY to
            """{"mtu":1700,"ipv6-mode":"ipv6_only","remote-dns-address":"https://dns.example/dns-query","tls-tricks":{"enable-fragment":true}}""")
        val applied = configured(original)
        val core = JsonParser.parseString(applied[NativePrivacySetup.CORE_KEY] as String).asJsonObject
        assertEquals(1400L, core["mtu"].asLong)
        assertEquals("ipv4_only", core["ipv6-mode"].asString)
        assertTrue(core["enable-tun"].asBoolean)
        assertTrue(core["tls-tricks"].asJsonObject["enable-fragment"].asBoolean)
        val restored = applied + NativePrivacySetup.restore(applied)
        assertEquals(1700L, restored["flutter.mtu"])
        val after = JsonParser.parseString(restored[NativePrivacySetup.CORE_KEY] as String).asJsonObject
        assertEquals("https://dns.example/dns-query", after["remote-dns-address"].asString)
        assertEquals("ipv6_only", after["ipv6-mode"].asString)
    }

    @Test fun automaticSetupResetsRoutingOverridesAndRestoresThemWithoutTouchingTransportFilters() {
        val original = mapOf<String, Any>("flutter.privacy-use-root" to true,
            "flutter.privacy-direct-packages" to "manual:", "flutter.privacy-proxy-packages" to "com.Example.App",
            "flutter.privacy-direct-domains" to "example.org", "flutter.privacy-proxy-services" to "manual:youtube",
            "flutter.per_app_proxy_mode" to "include", "flutter.privacy-modern-allow-udp" to true,
            "flutter.privacy-anonymization-block-quic" to true, "flutter.wifi-vpn-sharing" to true)
        val changes = NativePrivacySetup.configure(original)
        val applied = original + changes
        assertEquals(false, applied["flutter.privacy-use-root"])
        assertEquals("off", applied["flutter.per_app_proxy_mode"])
        for (key in listOf("privacy-direct-packages", "privacy-proxy-packages", "privacy-direct-domains", "privacy-proxy-services"))
            assertEquals("", applied["flutter.$key"])
        assertEquals(false, applied["flutter.wifi-vpn-sharing"])
        for (key in listOf("privacy-modern-allow-udp", "privacy-anonymization-block-quic"))
            assertFalse(changes.containsKey("flutter.$key"))
        val restored = applied + NativePrivacySetup.restore(applied)
        original.forEach { (key, value) -> assertEquals(key, value, restored[key]) }
    }

    @Test fun changedPolicyNoLongerReportsConfigured() {
        val applied = configured()
        assertFalse(NativePrivacySetup.isConfigured(applied + ("flutter.handbook-routing" to true)))
        assertFalse(NativePrivacySetup.isConfigured(applied + ("flutter.privacy-encrypted-dns" to false)))
        val core = JsonParser.parseString(applied[NativePrivacySetup.CORE_KEY] as String).asJsonObject
        core.addProperty("mtu", 1500)
        assertFalse(NativePrivacySetup.isConfigured(applied + (NativePrivacySetup.CORE_KEY to core.toString())))
    }

    private fun rejects(backup: String) {
        val original = configured() + (NativePrivacySetup.BACKUP_KEY to backup)
        try { NativePrivacySetup.restore(original); fail("Expected rejection") } catch (_: IllegalArgumentException) {}
        assertEquals(backup, original[NativePrivacySetup.BACKUP_KEY])
    }

    @Test fun malformedOrNonObjectBackupsRemainAvailableAndDoNotProduceWrites() {
        listOf("[1]", "null", "{broken", "true").forEach(::rejects)
    }

    @Test fun wrongTypesAreNotCoerced() {
        listOf("""{"mtu":"1400"}""", """{"publicDns":"true"}""", """{"serviceMode":42}""",
            """{"mtu":1400.5}""", """{"mtu":null}""").forEach(::rejects)
    }

    @Test fun invalidModesAndMtuBoundsAreRejected() {
        listOf("""{"serviceMode":"invalid"}""", """{"routingMode":"invalid"}""", """{"ipv6Mode":"invalid"}""",
            """{"mtu":575}""", """{"mtu":65536}""", """{"mtu":99999999999999999999999}""").forEach(::rejects)
    }

    @Test fun mtuBoundsAndLegacyAutoIpv6AreAccepted() {
        for (mtu in listOf(576L, 65535L)) {
            val values = configured() + (NativePrivacySetup.BACKUP_KEY to """{"mtu":$mtu,"ipv6Mode":"auto"}""")
            assertEquals(mtu, NativePrivacySetup.restore(values)["flutter.mtu"])
        }
    }

    @Test fun oversizedBackupsAndValuesAreRejected() {
        rejects(" ".repeat(512 * 1024 + 1))
        rejects("""{"customDirectPackages":"${"a".repeat(65537)}"}""")
    }

    @Test fun corruptCoreDoesNotGetSilentlyReplacedByDefaults() {
        try { NativePrivacySetup.configure(mapOf(NativePrivacySetup.CORE_KEY to "not json")); fail() }
        catch (_: IllegalArgumentException) {}
    }

    @Test fun noBackupCannotBeRestored() {
        assertFalse(NativePrivacySetup.canRestore(emptyMap<String, Any>()))
        try { NativePrivacySetup.restore(emptyMap<String, Any>()); fail() } catch (_: IllegalArgumentException) {}
    }
    @Test fun malformedBackupPreferenceIsNotOverwritten() {
        try { NativePrivacySetup.configure(mapOf(NativePrivacySetup.BACKUP_KEY to 42)); fail() }
        catch (_: IllegalArgumentException) {}
    }


    @Test fun automaticSetupWritesEffectiveDnsAndRoutingIntoTheCoreCache() {
        val original = mapOf<String, Any>(NativePrivacySetup.CORE_KEY to
            """{"execute-config-as-is":true,"enable-full-config":true,"strict-route":false,"direct-dns-address":"localhost","remote-dns-address":"tcp://9.9.9.9","mixed-port":12334}""",
            "flutter.privacy-full-tunnel" to true, "flutter.handbook-routing" to true)
        val applied = configured(original)
        val core = JsonParser.parseString(applied[NativePrivacySetup.CORE_KEY] as String).asJsonObject
        assertFalse(core["execute-config-as-is"].asBoolean)
        assertFalse(core["enable-full-config"].asBoolean)
        assertTrue(core["strict-route"].asBoolean)
        assertEquals("https://1.1.1.1/dns-query", core["remote-dns-address"].asString)
        assertEquals("https://1.1.1.1/dns-query", core["direct-dns-address"].asString)
        assertEquals(12334, core["mixed-port"].asInt)
        assertEquals(false, applied["flutter.privacy-full-tunnel"])
        assertEquals(false, applied["flutter.handbook-routing"])
        val restored = JsonParser.parseString(NativePrivacySetup.restore(applied)[NativePrivacySetup.CORE_KEY] as String).asJsonObject
        assertTrue(restored["execute-config-as-is"].asBoolean)
        assertTrue(restored["enable-full-config"].asBoolean)
        assertFalse(restored["strict-route"].asBoolean)
        assertEquals("localhost", restored["direct-dns-address"].asString)
        assertEquals("tcp://9.9.9.9", restored["remote-dns-address"].asString)
    }

    @Test fun manualEmptyAppListAndAppRestrictionCannotStillReportAutomaticConfigured() {
        val applied = configured()
        assertFalse(NativePrivacySetup.isConfigured(applied + ("flutter.privacy-direct-packages" to "manual:")))
        assertFalse(NativePrivacySetup.isConfigured(applied + ("flutter.per_app_proxy_mode" to "include")))
        assertFalse(NativePrivacySetup.isConfigured(applied + ("flutter.privacy-proxy-services" to "manual:")))
    }

    @Test fun legacyRawConfigFlagIsRestoredWhenTheNativeAliasWasAbsent() {
        val applied = configured(mapOf(NativePrivacySetup.CORE_KEY to """{"enable-full-config":true}"""))
        val restored = JsonParser.parseString(NativePrivacySetup.restore(applied)[NativePrivacySetup.CORE_KEY] as String).asJsonObject
        assertTrue(restored["enable-full-config"].asBoolean)
        assertTrue(restored["execute-config-as-is"].asBoolean)
    }

    @Test fun automaticSetupCannotBeMaskedByLanSharingOrProtocolFiltering() {
        val original = mapOf<String, Any>("flutter.wifi-vpn-sharing" to true,
            "flutter.privacy-modern-protocols-only" to true, NativePrivacySetup.CORE_KEY to """{"allow-connection-from-lan":true}""")
        val applied = configured(original)
        assertEquals(false, applied["flutter.wifi-vpn-sharing"])
        assertEquals(false, applied["flutter.privacy-modern-protocols-only"])
        assertFalse(JsonParser.parseString(applied[NativePrivacySetup.CORE_KEY] as String).asJsonObject["allow-connection-from-lan"].asBoolean)
        val restored = applied + NativePrivacySetup.restore(applied)
        assertEquals(true, restored["flutter.wifi-vpn-sharing"])
        assertEquals(true, restored["flutter.privacy-modern-protocols-only"])
        assertTrue(JsonParser.parseString(restored[NativePrivacySetup.CORE_KEY] as String).asJsonObject["allow-connection-from-lan"].asBoolean)
    }
}
