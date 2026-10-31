package com.hiddify.hiddify.nativediagnostics

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class NativeDiagnosticEndpointTest {
    @Test fun configurationArraysExposeEndpointsLikeSingleConfigurations() {
        val raw = """[{"outbounds":[{"type":"vless","tag":"one","server":"one.example","server_port":443}]},
            [{"endpoints":[{"type":"wireguard","tag":"two","server":"two.example","server_port":51820}]}]]"""
        assertEquals(listOf("one", "two"), NativeDiagnosticEndpoints.parse(raw).map { it.tag })
    }

    @Test fun encodedDetoursAreDetectedBeforeDirectConnectionTests() {
        val raw = """{"outbounds":[{"type":"vless","detour":"other"}]}"""
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
        assertTrue(NativeDiagnosticEndpoints.chained(encoded, emptySet()))
    }
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
    @Test fun clashYamlProfilesExposeTheirActualServerAddressAndPort() {
        val raw = """
            mixed-port: 7890
            proxies:
              - name: "Europe Node"
                type: vless
                server: edge.example.org
                port: 443
                uuid: hidden
                ws-opts:
                  headers:
                    Host: cdn.example.org
              - name: UDP Server
                type: hysteria2
                server: udp.example.net
                port: 443
              - name: "Backup"
                type: trojan
                server: backup.example.org
                port: "8443"
            proxy-groups:
              - name: automatic
                type: url-test
                proxies:
                  - "Europe Node"
        """.trimIndent()
        val result = NativeDiagnosticEndpoints.parse(raw)
        assertEquals(3, result.size)
        assertEquals(NativeDiagnosticEndpoint("Europe Node", "vless", "edge.example.org", 443), result[0])
        assertTrue(result[1].udp)
        assertEquals(NativeDiagnosticEndpoint("Backup", "trojan", "backup.example.org", 8443), result[2])
        assertEquals("backup.example.org", NativeDiagnosticEndpoints.selected(result, "Backup")?.host)
    }

    @Test fun effectiveCoreEndpointResolvesClashGroupTagMismatch() {
        val yaml = """
            proxies:
              - name: "Custom user-visible name"
                type: trojan
                server: first.example
                port: 443
              - name: "Other"
                type: vless
                server: second.example
                port: 443
        """.trimIndent()
        val core = com.hiddify.hiddify.nativecore.NativeOutbound(
            tag = "actual-core-outbound", name = "Chosen", type = "vless",
            selected = true, visible = true, delayMs = 120,
            host = "selected.example", port = 8443, upload = 0, download = 0,
            selectedChild = null
        )
        val parsed = NativeDiagnosticEndpoints.parse(yaml)
        assertNull(NativeDiagnosticEndpoints.selected(parsed, core.tag))
        assertEquals(
            NativeDiagnosticEndpoint("actual-core-outbound", "vless", "selected.example", 8443),
            NativeDiagnosticEndpoints.resolve(parsed, core.tag, core)
        )
        assertEquals(null, NativeDiagnosticEndpoints.resolve(parsed, "unknown", core.copy(isGroup = true)))
    }

    @Test fun effectiveUdpTransportNeverGetsMisclassifiedAsTcp() {
        val core = com.hiddify.hiddify.nativecore.NativeOutbound(
            tag = "udp", name = "UDP", type = "naive+quic",
            selected = true, visible = true, delayMs = 0,
            host = "udp.example", port = 443, upload = 0, download = 0, selectedChild = null
        )
        assertTrue(NativeDiagnosticEndpoints.resolve(emptyList(), core.tag, core)!!.udp)
        assertNull(NativeDiagnosticEndpoints.resolve(emptyList(), core.tag, core.copy(host = "", port = 0)))
    }

    @Test fun configBase64ClashIsDecodedWithoutChangingTCPSelection() {
        val yaml = "proxies:\n  - name: Server\n    type: ss\n    server: ss.example\n    port: 8388"
        val base64 = Base64.getEncoder().encodeToString(yaml.toByteArray())
        assertEquals("ss.example", NativeDiagnosticEndpoints.parse(base64).single().host)
    }

    @Test fun selectedGroupResolvesOnlyItsChosenChildNotAnUnrelatedNode() {
        fun outbound(tag: String, host: String, port: Int, group: Boolean = false, child: String? = null) =
            com.hiddify.hiddify.nativecore.NativeOutbound(
                tag = tag, name = tag, type = if (group) "selector" else "trojan",
                selected = true, visible = true, delayMs = 0, host = host,
                port = port, upload = 0, download = 0, selectedChild = child,
                isGroup = group, selectedChildTag = child
            )
        val first = outbound("first", "one.example", 443)
        val second = outbound("second", "two.example", 8443)
        val selector = outbound("main", "", 0, group = true, child = "second")
        assertEquals(
            NativeDiagnosticEndpoint("second", "trojan", "two.example", 8443),
            NativeDiagnosticEndpoints.resolve(emptyList(), "main", selector, listOf(first, second)),
        )
        assertNull(NativeDiagnosticEndpoints.resolve(emptyList(), "main", selector, listOf(first)))
        val cyclic = selector.copy(selectedChildTag = "main")
        assertNull(NativeDiagnosticEndpoints.resolve(emptyList(), "main", cyclic, listOf(cyclic)))
    }

    @Test fun groupChildTagCanMatchConfiguredNodeWhenCoreOmitsHost() {
        val selected = NativeDiagnosticEndpoints.parse(
            """{"outbounds":[{"type":"vless","tag":"choice","server":"choice.example","server_port":443}]}"""
        )
        val group = com.hiddify.hiddify.nativecore.NativeOutbound(
            tag = "select", name = "Selection", type = "selector",
            selected = true, visible = true, delayMs = 0, host = "", port = 0,
            upload = 0, download = 0, selectedChild = "choice", isGroup = true,
            selectedChildTag = "choice",
        )
        assertEquals("choice.example", NativeDiagnosticEndpoints.resolve(selected, group.tag, group)?.host)
    }

}
