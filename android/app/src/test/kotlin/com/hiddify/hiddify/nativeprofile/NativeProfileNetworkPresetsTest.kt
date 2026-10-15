package com.hiddify.hiddify.nativeprofile

import org.junit.Assert.*
import org.junit.Test

class NativeProfileNetworkPresetsTest {
    @Test fun generatedHeadersKeepProviderAuthorizationAndOtherPrivateValues() {
        val original = NativeSubscriptionRequestOptions("custom-client", "Authorization: opaque-token\nx-provider: private-value")
        val result = NativeProfileNetworkPresets.withMetadata(NativeProfileNetworkPresets.withAccept(original, "application/json"), true, "1.1.0")
        assertEquals("opaque-token", result.parsedHeaders()["authorization"])
        assertEquals("private-value", result.parsedHeaders()["x-provider"])
        assertEquals("Android", result.parsedHeaders()["x-device-os"])
        assertEquals("1.1.0", result.parsedHeaders()["x-app-version"])
        assertEquals("application/json", result.parsedHeaders()["accept"])
        val automatic = NativeProfileNetworkPresets.automatic(NativeProfileNetworkOptions(result, NativeProfileTlsOptions(fingerprint = "firefox")))
        assertEquals(mapOf("authorization" to "opaque-token", "x-provider" to "private-value"), automatic.request.parsedHeaders())
        assertEquals("", automatic.request.userAgent)
        assertEquals(NativeProfileTlsOptions(), automatic.tls)
    }
    @Test fun headerLimitFailuresDoNotAlterProviderValues() {
        val request = NativeSubscriptionRequestOptions(headers = (1..32).joinToString("\n") { "x-private-$it: preserved-$it" })
        val original = request.parsedHeaders()
        assertTrue(runCatching { NativeProfileNetworkPresets.withAccept(request, "application/json") }.isFailure)
        assertTrue(runCatching { NativeProfileNetworkPresets.withMetadata(request, true, "1.1.0") }.isFailure)
        assertEquals(original, request.parsedHeaders())
        assertEquals(original, NativeProfileNetworkPresets.automatic(NativeProfileNetworkOptions(request)).request.parsedHeaders())
    }
    @Test fun changingTargetClearsIncompatibleOptionsAndPreviousCertificateName() {
        val targets = listOf(NativeProfileTlsTarget("tcp", "a.example", true, true),
            NativeProfileTlsTarget("reality", "b.example", true, false), NativeProfileTlsTarget("quic", "c.example", false, false))
        val source = NativeProfileTlsOptions("a.example", "firefox", "h2", "both", "100", "1.2", "1.3", "tcp", "off")
        val reality = NativeProfileNetworkPresets.withTarget(source, "reality", targets)
        assertEquals("firefox", reality.fingerprint)
        assertEquals("", reality.serverName); assertEquals("", reality.sniMode)
        assertEquals("", reality.fragmentMode); assertEquals("", reality.alpn)
        assertEquals("", reality.minVersion); assertEquals("", reality.fallbackDelayMs)
        assertEquals("", NativeProfileNetworkPresets.withTarget(source.copy(fingerprint = "off"), "reality", targets).fingerprint)
        assertEquals(NativeProfileTlsOptions(targetTag = "quic"), NativeProfileNetworkPresets.withTarget(source, "quic", targets))
    }
    @Test fun allClientTemplatesProduceValidSubscriptionRequests() {
        assertTrue(NativeProfileNetworkPresets.clients.size > 10)
        NativeProfileNetworkPresets.clients.forEach {
            val request = NativeSubscriptionRequestOptions(it.agent).request("https://example.com/sub")
            assertEquals(it.agent.ifEmpty { NativeSubscriptionRequestOptions.DEFAULT_USER_AGENT }, request.header("User-Agent"))
        }
    }
    @Test fun sniAndNativeTlsPreserveCertificateValidationAndReality() {
        val source = """{"outbounds":[{"type":"http","tag":"tcp","tls":{"enabled":true,"server_name":"cert.example","insecure":false,"certificate_public_key_sha256":["pin"],"utls":{"enabled":true,"fingerprint":"chrome"}}},{"type":"vless","tag":"reality","tls":{"enabled":true,"reality":{"enabled":true},"utls":{"enabled":true,"fingerprint":"chrome"}}}]}"""
        val options = NativeProfileTlsOptions(fingerprint = "off", sniMode = "off")
        assertEquals(options, NativeProfileTlsOptions.fromJson(options.toJson()))
        val changed = NativeJsonDocument.parse(options.apply(source)).asJsonObject.getAsJsonArray("outbounds")
        val tls = changed[0].asJsonObject.getAsJsonObject("tls")
        assertFalse(tls.get("insecure").asBoolean)
        assertEquals("pin", tls.getAsJsonArray("certificate_public_key_sha256")[0].asString)
        assertEquals("cert.example", tls.get("server_name").asString)
        assertTrue(tls.get("disable_sni").asBoolean)
        assertFalse(tls.getAsJsonObject("utls").get("enabled").asBoolean)
        assertEquals(NativeJsonDocument.parse(source).asJsonObject.getAsJsonArray("outbounds")[1], changed[1])
        assertThrows(IllegalArgumentException::class.java) { options.apply("trojan://pass@server:443?security=tls") }
    }
}
