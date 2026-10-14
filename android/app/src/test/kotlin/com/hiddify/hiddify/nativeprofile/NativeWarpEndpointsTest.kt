package com.hiddify.hiddify.nativeprofile

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class NativeWarpEndpointsTest {
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
    private fun content() = NativeWarpAccount.parse("""
        [Interface]
        PrivateKey = $key
        Address = 172.16.0.2/32
        [Peer]
        PublicKey = $key
        AllowedIPs = 0.0.0.0/0
        Endpoint = 162.159.195.1:500
    """.trimIndent()).content(NativeWarpMode.AWG)

    @Test fun retriesPreserveCredentialsAndObfuscationAndTryOriginalFirst() {
        val original = content()
        val variants = NativeWarpEndpoints.candidates(original, "WARP + AmneziaWG")
        assertEquals(5, variants.size)
        assertEquals(NativeJsonDocument.parse(original), NativeJsonDocument.parse(variants.first()))
        val originalNode = NativeJsonDocument.parse(original).asJsonObject.getAsJsonArray("endpoints")[0].asJsonObject
        val peers = mutableSetOf<String>()
        variants.forEach {
            val node = NativeJsonDocument.parse(it).asJsonObject.getAsJsonArray("endpoints")[0].asJsonObject
            assertEquals(originalNode.get("private_key"), node.get("private_key"))
            assertEquals(originalNode.get("awg"), node.get("awg"))
            assertEquals(originalNode.get("address"), node.get("address"))
            val peer = node.getAsJsonArray("peers")[0].asJsonObject
            peers += peer.get("address").asString + ":" + peer.get("port").asString
            assertEquals(key, peer.get("public_key").asString)
            assertFalse(peer.get("address").asString.startsWith("162.159.197."))
        }
        assertEquals(5, peers.size)
    }

    @Test fun doesNotRewriteGeneralOrCustomizedConfigurations() {
        assertTrue(NativeWarpEndpoints.candidates(content(), "My custom VPN").isEmpty())
        assertTrue(NativeWarpEndpoints.candidates(content().replace("162.159.195.1", "10.0.0.1"), "WARP + AmneziaWG").isEmpty())
        assertTrue(NativeWarpEndpoints.candidates(content().replace("1280", "1420"), "WARP + AmneziaWG").isEmpty())
        assertTrue(NativeWarpEndpoints.candidates("{\"outbounds\":[]}", "Cloudflare WARP").isEmpty())
        assertTrue(NativeWarpEndpoints.candidates("broken", "Cloudflare WARP").isEmpty())
    }

    @Test fun noSniTransportPreservesNormalCertificateVerification() {
        val normal = okhttp3.OkHttpClient()
        val fallback = NativeWarpApiTransport.fallback(normal)
        assertSame(normal.x509TrustManager, fallback.x509TrustManager)
        assertSame(normal.hostnameVerifier, fallback.hostnameVerifier)
        assertFalse(fallback.followRedirects)
    }
}
