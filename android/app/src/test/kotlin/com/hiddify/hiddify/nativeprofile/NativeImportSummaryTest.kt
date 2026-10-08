package com.hiddify.hiddify.nativeprofile

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class NativeImportSummaryTest {
    @Test fun jsonCountsEndpointsAndDuplicatesWithoutUtilityOutbounds() {
        val summary = NativeImportSummary.parse("""{
          "dns": {}, "route": {},
          "outbounds": [
            {"type":"selector","tag":"proxy"},
            {"type":"direct"},
            {"type":"vless","tag":"one","server":"example.org","server_port":443},
            {"type":"vless","tag":"two","server":"example.org","server_port":443},
            {"protocol":"future-protocol","settings":{"servers":[{"address":"example.net","port":443}]}}
          ],
          "endpoints": [{"type":"masque-client","name":"MASQUE"},{"type":"wireguard"}]
        }""")
        assertEquals(5, summary.servers)
        assertEquals(1, summary.duplicates)
        assertEquals(1, summary.unknownTypes)
        assertEquals(listOf("dns", "route"), summary.overrides)
    }

    @Test fun linkFragmentsDoNotHideDuplicatesAndCredentialsAreNotReturned() {
        val summary = NativeImportSummary.parse("""
            vless://secret-token@[2001:db8::1]:443?security=reality#First
            vless://secret-token@[2001:db8::1]:443?security=reality#Second
            https://subscription.example/private-token
            hiddify://import/https://example.org
            madeup://example.org:443#Unknown
        """.trimIndent())
        assertEquals(3, summary.servers)
        assertEquals(1, summary.duplicates)
        assertEquals(1, summary.unknownTypes)
        assertFalse(summary.toString().contains("secret-token"))
        assertFalse(summary.toString().contains("example.org"))
    }

    @Test fun base64SubscriptionsMatchTheirDecodedLinks() {
        val links = "trojan://secret@example.org:443#One\ntuic://secret@example.org:443#Two"
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(links.toByteArray())
        assertEquals(NativeImportSummary.parse(links), NativeImportSummary.parse(encoded))
    }

    @Test fun yamlCountsTypesAndExposesRoutingOverrideNamesOnly() {
        val summary = NativeImportSummary.parse("""
            proxies:
              - name: First
                type: hysteria2
              - name: Second
                type: shadowsocks
              - name: Unknown
                type: new-protocol
            dns:
              enable: true
            rules:
              - MATCH,proxy
            rule-providers:
              private: {}
        """.trimIndent())
        assertEquals(3, summary.servers)
        assertEquals(1, summary.unknownTypes)
        assertEquals(listOf("dns", "rules", "rule-providers"), summary.overrides)
    }

    @Test fun httpWarningAndHeaderOverridesRemainSeparateFromCounts() {
        val summary = NativeImportSummary.parse("vless://secret@example.org:443",
            linkedSetOf("profile-title", "direct-dns-address", "enable-fragment", "chain-status"), true)
        assertTrue(summary.insecureHttp)
        assertEquals(listOf("direct-dns-address", "enable-fragment", "chain-status"), summary.overrides)
        assertEquals(1, summary.servers)
    }

    @Test fun malformedAndUtilityOnlyContentDoesNotInventServers() {
        assertEquals(0, NativeImportSummary.parse("not a configuration").servers)
        assertEquals(0, NativeImportSummary.parse("""{"outbounds":[{"type":"reject"},null,3],"endpoints":{}}""").servers)
        assertEquals(0, NativeImportSummary.parse("vless://host/%invalid").servers)
    }
}
