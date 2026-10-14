package com.hiddify.hiddify.nativeprofile

import org.junit.Assert.*
import org.junit.Test

class NativeProfileTlsTargetsTest {
    @Test fun distinguishesTcpRealityQuicAndNonTlsEndpoints() {
        val raw = """{"outbounds":[
          {"type":"vless","tag":"tcp","tls":{"enabled":true,"server_name":"cert.example"}},
          {"type":"vless","tag":"reality","tls":{"enabled":true,"reality":{"enabled":true}}},
          {"type":"hysteria2","tag":"quic","tls":{"enabled":true}},
          {"type":"vless","tag":"plain"}],"endpoints":[{"type":"awg"}]}"""
        val targets = NativeProfileTlsTargets.inspect(raw)
        assertEquals(listOf("tcp", "reality", "quic"), targets.map { it.tag })
        assertEquals("cert.example", targets.first().serverName)
        assertTrue(targets[0].fragment)
        assertTrue(targets[1].fingerprint); assertFalse(targets[1].fragment)
        assertFalse(targets[2].fingerprint); assertFalse(targets[2].fragment)
    }
    @Test fun readsArraysOfOutboundsAndArraysOfConfigurations() {
        val node = """{"type":"trojan","tag":"one","tls":{"enabled":true}}"""
        assertEquals(listOf("one"), NativeProfileTlsTargets.inspect("[$node]").map { it.tag })
        assertEquals(listOf("one"), NativeProfileTlsTargets.inspect("[{\"outbounds\":[$node]}]").map { it.tag })
    }
    @Test fun parsesLinkNamesAndPreservesProviderNamesRatherThanGuessing() {
        val raw = "vless://id@host.example:443?security=tls&sni=cert.example#First%20server\n" +
            "trojan://secret@other.example:443?security=reality#Second\ninvalid"
        val targets = NativeProfileTlsTargets.inspect(raw)
        assertEquals("First server", targets[0].tag)
        assertEquals("cert.example", targets[0].serverName)
        assertEquals("", targets[1].serverName)
        assertFalse(targets[1].fragment)
        assertTrue(NativeProfileTlsTargets.inspect("{\"endpoints\":[{\"type\":\"awg\"}]}").isEmpty())
    }
}
