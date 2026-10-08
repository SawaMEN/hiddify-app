package com.hiddify.hiddify.nativediagnostics

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class NativeDiagnosticEndpointTest {
    @Test fun mixedProfilesUseTheSelectedTagAndNeverGuessAmongSeveralServers() {
        val endpoints = NativeDiagnosticEndpoints.parse("""{"outbounds":[{"type":"selector","tag":"pick"},{"type":"vless","tag":"a","server":"a.example","server_port":443},{"type":"tuic","tag":"b","server":"b.example","server_port":8443}]}""")
        assertEquals(2, endpoints.size)
        assertEquals("a.example", NativeDiagnosticEndpoints.selected(endpoints, "a")?.host)
        assertTrue(NativeDiagnosticEndpoints.selected(endpoints, "b")!!.udp)
        assertNull(NativeDiagnosticEndpoints.selected(endpoints, "missing"))
    }
    @Test fun xrayAndSingleEndpointFallbackArePreserved() {
        val endpoints = NativeDiagnosticEndpoints.parse("""{"outbounds":[{"protocol":"vmess","settings":{"vnext":[{"address":"x.example","port":1234}]}}]}""")
        assertEquals(1234, NativeDiagnosticEndpoints.selected(endpoints, null)?.port)
    }
    @Test fun vmessAndBase64SubscriptionsKeepOnlyReachabilityFields() {
        val payload = Base64.getEncoder().encodeToString("""{"add":"test.example","port":"443","ps":"my tag","id":"secret"}""".toByteArray())
        val links = Base64.getEncoder().encodeToString("vmess://$payload".toByteArray())
        val endpoint = NativeDiagnosticEndpoints.parse(links).single()
        assertEquals(NativeDiagnosticEndpoint("my tag", "vmess", "test.example", 443), endpoint)
    }
    @Test fun chainedAndUdpEndpointsAreNotMisrepresentedAsTcpSuccess() {
        assertTrue(NativeDiagnosticEndpoints.chained("""{"outbounds":[{"type":"vless","detour":"first"}]}""", emptySet()))
        assertTrue(NativeDiagnosticEndpoints.chained("", setOf("enable-chain")))
        assertFalse(NativeDiagnosticEndpoints.chained("vless://user@test.example:443", emptySet()))
        assertTrue(NativeDiagnosticEndpoint("wg", "wg", "test.example", 1234).udp)
    }
    @Test fun fragmentDoesNotDisableTcpAndLegacySsEndpointsAreDecoded() {
        assertFalse(NativeDiagnosticEndpoints.chained("", setOf("enable-fragment", "enable-padding")))
        assertFalse(NativeDiagnosticEndpoints.chained("{\"detour\":\"\"}", emptySet()))
        assertFalse(NativeDiagnosticEndpoints.chained("{\"note\":\"detour\"}", emptySet()))
        val encoded = Base64.getEncoder().encodeToString("aes-256-gcm:secret@test.example:1234".toByteArray())
        assertEquals("test.example", NativeDiagnosticEndpoints.parse("ss://$encoded#tag").single().host)
        assertEquals(1234, NativeDiagnosticEndpoints.parse("ss://$encoded#tag").single().port)
        assertTrue(NativeDiagnosticEndpoint("tag", "hysteria2_legacy", "server", 443).udp)
    }
}
