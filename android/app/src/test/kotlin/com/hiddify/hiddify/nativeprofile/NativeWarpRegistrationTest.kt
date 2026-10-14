package com.hiddify.hiddify.nativeprofile

import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.bouncycastle.math.ec.rfc7748.X25519
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import java.util.concurrent.CancellationException

class NativeWarpRegistrationTest {
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
    private fun profile() = """{"result":{"key":"$key","warp_enabled":true,"config":{"client_id":"Af7/","interface":{"addresses":{"v4":"172.16.0.2","v6":"2606:4700:110:1234::2"}},"peers":[{"public_key":"$key","endpoint":{"v4":"162.159.192.7:0","v6":"[2606:4700:d0::1]:0","host":"engage.cloudflareclient.com:2408","ports":[2408,500,1701,4500]}}]}},"success":true}"""
    private fun endpoint(raw: String) = NativeJsonDocument.parse(raw).asJsonObject.getAsJsonArray("endpoints")[0].asJsonObject

    @Test fun cloudflareMaterialPreservesClientIdAndUsesLiteralIpv4AndKeepalive() {
        val node = endpoint(NativeWarpAccount.parse(profile(), key).content(NativeWarpMode.STANDARD))
        assertEquals("wireguard", node.get("type").asString)
        val peer = node.getAsJsonArray("peers")[0].asJsonObject
        assertEquals("162.159.192.7", peer.get("address").asString)
        assertEquals(2408, peer.get("port").asInt)
        assertEquals(listOf(1, 254, 255), peer.getAsJsonArray("reserved").map { it.asInt })
        assertEquals(25, peer.get("persistent_keepalive_interval").asInt)
        assertTrue(node.getAsJsonArray("address").all { '/' in it.asString })
        assertFalse(node.has("unique_identifier"))
        assertFalse(node.has("profile"))
    }

    @Test fun directRegistrationSendsOnlyPublicKeyAndValidatesBeforeReturning() {
        var sent: JsonObject? = null
        var calls = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            assertEquals("POST", chain.request().method)
            assertEquals("application/json; charset=utf-8", chain.request().body!!.contentType().toString())
            assertEquals("okhttp/3.12.1", chain.request().header("User-Agent"))
            val buffer = Buffer(); chain.request().body!!.writeTo(buffer)
            sent = NativeJsonDocument.parse(buffer.readUtf8()).asJsonObject
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(profile().toResponseBody()).build()
        }.build()
        val repository = NativeWarpProfilesRepository(http, emptyList(), "https://example.com/reg")
        val node = repository.generate(NativeProfileImportCancellation(), NativeWarpMode.STANDARD) { endpoint(it) }
        val privateKey = Base64.getDecoder().decode(node.get("private_key").asString)
        val publicKey = ByteArray(32).also { X25519.generatePublicKey(privateKey, 0, it, 0) }
        assertEquals(Base64.getEncoder().encodeToString(publicKey), sent!!.get("key").asString)
        assertFalse(sent!!.has("private_key")); assertFalse(sent!!.has("token"))
        assertNotEquals(key, node.get("private_key").asString)
        assertEquals(1, calls)
    }

    @Test fun registrationFailureNeverReturnsEmptyProfileOrUsesUnapprovedBackup() {
        var calls = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(403).message("fixture")
                .body("secret denied".toResponseBody()).build()
        }.build()
        val repository = NativeWarpProfilesRepository(http, listOf(Request.Builder().url("https://backup.example.com").build()))
        val error = runCatching { repository.generate(NativeProfileImportCancellation(), NativeWarpMode.AWG) { it } }.exceptionOrNull()
        assertTrue(error is NativeWarpGenerationException)
        assertTrue(error!!.message!!.contains("HTTP 403")); assertFalse(error.message!!.contains("secret"))
        assertEquals(1, calls)
    }

    @Test fun cancellationBeforeRegistrationMakesNoHttpRequest() {
        val cancellation = NativeProfileImportCancellation(); cancellation.cancel()
        var calls = 0
        val http = OkHttpClient.Builder().addInterceptor { calls++; error("Must not download") }.build()
        val error = runCatching { NativeWarpProfilesRepository(http).generate(cancellation, NativeWarpMode.AWG) { it } }.exceptionOrNull()
        assertTrue(error is CancellationException); assertEquals(0, calls)
    }

    @Test fun malformedAccountNeverBecomesUsableEndpoint() {
        for (bad in listOf(profile().replace("162.159.192.7", "localhost"), profile().replace("Af7/", "AQ=="),
            profile().replace("\"warp_enabled\":true", "\"warp_enabled\":false"), profile().replace("172.16.0.2", "0.0.0.0"))) {
            assertTrue(runCatching { NativeWarpAccount.parse(bad, key) }.isFailure)
        }
    }
}
