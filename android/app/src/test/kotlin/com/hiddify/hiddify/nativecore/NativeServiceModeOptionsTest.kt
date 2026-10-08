package com.hiddify.hiddify.nativecore

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class NativeServiceModeOptionsTest {
    @Test fun freshAndroidVpnConfigCreatesTunInbound() {
        val root = JsonParser.parseString(NativeServiceModeOptions.apply("", true)).asJsonObject
        assertTrue(root.get("enable-tun").asBoolean)
    }

    @Test fun vpnModeOverridesOldDisabledTunSettingWithoutLosingOtherSettings() {
        val root = JsonParser.parseString(NativeServiceModeOptions.apply(
            """{"enable-tun":false,"mixed-port":13337,"tls-tricks":{"enable-fragment":true}}""", true,
        )).asJsonObject
        assertTrue(root.get("enable-tun").asBoolean)
        assertEquals(13337, root.get("mixed-port").asInt)
        assertTrue(root.getAsJsonObject("tls-tricks").get("enable-fragment").asBoolean)
    }

    @Test fun proxyModeNeverRequestsAndroidTun() {
        val root = JsonParser.parseString(NativeServiceModeOptions.apply(
            """{"enable-tun":true,"enable-mixed-port":true}""", false,
        )).asJsonObject
        assertFalse(root.get("enable-tun").asBoolean)
        assertTrue(root.get("enable-mixed-port").asBoolean)
    }

    @Test fun androidDefaultIpv4OnlyIsAppliedToTunConfig() {
        assertTrue(NativeServiceModeOptions.isIpv4Only("{}", "ipv4_only"))
        val root = JsonParser.parseString(
            NativeServiceModeOptions.apply("""{"tun-implementation":"mixed"}""", true, true)
        ).asJsonObject
        assertTrue(root.get("enable-tun").asBoolean)
        assertEquals("ipv4_only", root.get("ipv6-mode").asString)
        assertEquals("mixed", root.get("tun-implementation").asString)
    }

    @Test fun explicitCoreIpv6ModeTakesPrecedenceOverLegacyPreference() {
        assertFalse(NativeServiceModeOptions.isIpv4Only("""{"ipv6-mode":"prefer_ipv6"}""", "ipv4_only"))
        assertTrue(NativeServiceModeOptions.isIpv4Only("""{"ipv6-mode":"ipv4_only"}""", "prefer_ipv6"))
    }

    @Test fun dualStackVpnRetainsExplicitIpv6Policy() {
        val root = JsonParser.parseString(
            NativeServiceModeOptions.apply("""{"ipv6-mode":"prefer_ipv6"}""", true, false)
        ).asJsonObject
        assertEquals("prefer_ipv6", root.get("ipv6-mode").asString)
        assertTrue(root.get("enable-tun").asBoolean)
    }

    @Test fun proxyModeDoesNotOverrideIpv6Policy() {
        val root = JsonParser.parseString(
            NativeServiceModeOptions.apply("""{"ipv6-mode":"prefer_ipv4"}""", false, true)
        ).asJsonObject
        assertEquals("prefer_ipv4", root.get("ipv6-mode").asString)
        assertFalse(root.get("enable-tun").asBoolean)
    }

    @Test fun inputIsNotMutatedAndInvalidSettingsAreRejected() {
        val original = """{"enable-tun":false,"region":"ru"}"""
        NativeServiceModeOptions.apply(original, true)
        assertEquals("""{"enable-tun":false,"region":"ru"}""", original)
        assertThrows(IllegalStateException::class.java) {
            NativeServiceModeOptions.apply("[]", true)
        }
    }
}
