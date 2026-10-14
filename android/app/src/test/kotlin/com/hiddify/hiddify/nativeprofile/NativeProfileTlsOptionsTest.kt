package com.hiddify.hiddify.nativeprofile

import org.junit.Assert.*
import org.junit.Test

class NativeProfileTlsOptionsTest {
    private val source = """{"route":{"rules":[{"domain":["local.example"],"outbound":"direct"}]},"outbounds":[
        {"type":"vless","tag":"tcp","server":"edge.example","uuid":"keep","tls":{"enabled":true,"server_name":"old.example","insecure":false,"certificate_public_key_sha256":["pin"],"utls":{"enabled":true,"fingerprint":"firefox"}}},
        {"type":"vless","tag":"reality","tls":{"enabled":true,"reality":{"enabled":true,"public_key":"keep"}}},
        {"type":"hysteria2","tag":"quic","tls":{"enabled":true}},
        {"type":"direct","tag":"direct"}]}"""

    @Test fun overridesOnlySelectedServerAndRetainsCertificateChecksAndRoute() {
        val result = NativeJsonDocument.parse(NativeProfileTlsOptions(serverName = "new.example", fingerprint = "chrome", targetTag = "tcp").apply(source)).asJsonObject
        val original = NativeJsonDocument.parse(source).asJsonObject
        assertEquals(original.get("route"), result.get("route"))
        val servers = result.getAsJsonArray("outbounds")
        val tls = servers[0].asJsonObject.getAsJsonObject("tls")
        assertEquals("new.example", tls.get("server_name").asString)
        assertFalse(tls.get("insecure").asBoolean)
        assertEquals("pin", tls.getAsJsonArray("certificate_public_key_sha256")[0].asString)
        assertEquals("keep", servers[0].asJsonObject.get("uuid").asString)
        assertEquals(original.getAsJsonArray("outbounds")[1], servers[1])
        assertEquals(original.getAsJsonArray("outbounds")[2], servers[2])
    }

    @Test fun modernFragmentationAppliesToTcpOnly() {
        val result = NativeJsonDocument.parse(NativeProfileTlsOptions(fragmentMode = "both", fallbackDelayMs = "50").apply(source)).asJsonObject.getAsJsonArray("outbounds")
        val tls = result[0].asJsonObject.getAsJsonObject("tls")
        assertTrue(tls.get("fragment").asBoolean)
        assertTrue(tls.get("record_fragment").asBoolean)
        assertEquals("50ms", tls.get("fragment_fallback_delay").asString)
        assertFalse(result[1].asJsonObject.getAsJsonObject("tls").has("fragment"))
        assertFalse(result[2].asJsonObject.getAsJsonObject("tls").has("fragment"))
    }

    @Test fun vlessLinksPreserveSecretsTransportAndEscapedNames() {
        val source = "# comment\nvless://uuid@host.example:443?security=tls&type=tcp&sni=old.example&path=%2Fa%3Fb#My%20server%2B1\nss://keep"
        val result = NativeProfileTlsOptions(serverName = "new.example", fingerprint = "chrome", alpn = "h2,http/1.1", fragmentMode = "tcp", targetTag = "My server+1").apply(source)
        assertTrue(result.contains("vless://uuid@host.example:443?security=tls&type=tcp&path=%2Fa%3Fb"))
        assertTrue(result.contains("sni=new.example"))
        assertTrue(result.contains("fragment=tlshello%2C10-30%2C2-8"))
        assertTrue(result.endsWith("#My%20server%2B1\nss://keep"))
        assertFalse(result.contains("old.example"))
    }

    @Test fun unsupportedFragmentationNeverSilentlySucceeds() {
        val link = "vless://uuid@host:443?security=reality&sni=old#one"
        assertThrows(IllegalArgumentException::class.java) { NativeProfileTlsOptions(fragmentMode = "tcp").apply(link) }
        assertThrows(IllegalArgumentException::class.java) { NativeProfileTlsOptions(fragmentMode = "record").apply(link) }
        assertThrows(IllegalArgumentException::class.java) { NativeProfileTlsOptions(serverName = "new", targetTag = "missing").apply(source) }
        assertThrows(IllegalArgumentException::class.java) { NativeProfileTlsOptions(serverName = "new").apply("ss://keep") }
    }

    @Test fun localOverridesPersistWithoutChangingProviderSourceOrOtherPreferences() {
        val previous = """{"name":"mine","isAutoUpdateDisable":true,"enableWarp":true}"""
        val choices = NativeProfileNetworkOptions(NativeSubscriptionRequestOptions("Provider/1", "x-device-os: Android"), NativeProfileTlsOptions(serverName = "new.example"))
        val stored = choices.mergeOverride(previous)
        assertEquals(choices, NativeProfileNetworkOptions.fromOverride(stored))
        assertTrue(NativeJsonDocument.parse(stored).asJsonObject.get("enableWarp").asBoolean)
        val freshSource = source.replace("old.example", "provider-updated.example")
        assertTrue(choices.tls.apply(freshSource).contains("new.example"))
        assertTrue(freshSource.contains("provider-updated.example"))
        val reset = NativeProfileNetworkOptions().mergeOverride(stored)
        assertEquals(NativeJsonDocument.parse(previous), NativeJsonDocument.parse(reset))
        assertEquals(freshSource, NativeProfileNetworkOptions.fromOverride(reset).tls.apply(freshSource))
    }

    @Test fun defaultsDoNotParseOrRewriteUnrelatedProfiles() {
        listOf(source, "ss://keep", "proxies:\n  yaml", "vmess://legacy").forEach {
            assertEquals(it, NativeProfileTlsOptions().apply(it))
        }
    }

    @Test fun mixedLinkSubscriptionFragmentsTcpAndPreservesReality() {
        val tcp = "vless://uuid@tcp:443?security=tls#tcp"
        val reality = "vless://uuid@reality:443?security=reality&pbk=keep&sni=keep#reality"
        val result = NativeProfileTlsOptions(fragmentMode = "tcp").apply("$tcp\n$reality")
        assertTrue(result.substringBefore('\n').contains("fragment="))
        assertEquals(reality, result.substringAfter('\n'))
    }

    @Test fun vmessAndTrojanLinksApplyTlsWithoutChangingAuthentication() {
        val vmess = """{"v":"2","ps":"vmess","add":"edge.example","port":"443","id":"keep-id","tls":"tls","sni":"old"}"""
        val link = "vmess://" + java.util.Base64.getEncoder().encodeToString(vmess.toByteArray())
        val result = NativeProfileTlsOptions(serverName = "new.example", fingerprint = "chrome").apply(link)
        val parsed = NativeJsonDocument.parse(String(java.util.Base64.getDecoder().decode(result.substringAfter("://")))).asJsonObject
        assertEquals("keep-id", parsed.get("id").asString)
        assertEquals("new.example", parsed.get("sni").asString)
        assertEquals("chrome", parsed.get("fp").asString)
        val trojan = NativeProfileTlsOptions(serverName = "new.example").apply("trojan://keep-password@host:443?security=tls&SNI=old#trojan")
        assertTrue(trojan.startsWith("trojan://keep-password@host:443?security=tls"))
        assertTrue(trojan.contains("sni=new.example"))
        assertFalse(trojan.contains("SNI=old"))
    }

    @Test fun invalidValuesAreRejectedBeforeSaving() {
        listOf("bad/name", "bad\nname", "-bad.example", "bad..example").forEach { sni ->
            assertThrows(IllegalArgumentException::class.java) { NativeProfileTlsOptions(serverName = sni).validated() }
        }
        assertThrows(IllegalArgumentException::class.java) { NativeProfileTlsOptions(fallbackDelayMs = "0").validated() }
        assertThrows(IllegalArgumentException::class.java) { NativeProfileTlsOptions(minVersion = "1.3", maxVersion = "1.2").validated() }
        assertThrows(IllegalArgumentException::class.java) { NativeProfileTlsOptions(fingerprint = "unknown").validated() }
    }
}
