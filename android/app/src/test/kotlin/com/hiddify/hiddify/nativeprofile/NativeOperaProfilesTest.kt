package com.hiddify.hiddify.nativeprofile

import com.google.gson.JsonParser
import okhttp3.Request
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Test

class NativeOperaProfilesTest {
    @Test fun catalogRequiresExplanationAndSupportsOnlyKnownRegions() {
        assertEquals(3, NativeOperaProfiles.profiles.size)
        NativeOperaProfiles.profiles.forEach {
            assertNotNull(NativeOperaProfiles.region(it.url))
            assertTrue(it.consentEn.contains("UDP"))
            assertTrue(it.consentRu!!.contains("данные доступа"))
            assertTrue(it.consentRu.contains("анонимности"))
        }
        listOf(null, "builtin:opera-eu", "builtin:opera-EU/", "https://example.com", "builtin:opera-EU?url=https://evil.test").forEach {
            assertNull(NativeOperaProfiles.region(it))
        }
    }

    @Test fun generatedConfigUsesVerifiedTlsAndApiEndpointsWithoutDirectFallback() {
        val servers = JsonParser.parseString("""[{"ip":"77.111.244.26","ports":[443]},{"ip":"77.111.247.51","ports":[]}]""").asJsonArray
        val document = JsonParser.parseString(NativeOperaProfiles.content("EU", "device", "token", servers)).asJsonObject
        val outbounds = document.getAsJsonArray("outbounds")
        assertEquals(2, outbounds.size())
        outbounds.forEach {
            val outbound = it.asJsonObject
            assertEquals("http", outbound.get("type").asString)
            assertEquals(443, outbound.get("server_port").asInt)
            assertEquals("token", outbound.get("password").asString)
            val tls = outbound.getAsJsonObject("tls")
            assertTrue(tls.get("enabled").asBoolean)
            assertEquals("eu0.sec-tunnel.com", tls.get("server_name").asString)
            assertFalse(tls.has("insecure"))
        }
    }

    @Test fun invalidServersAreRejected() {
        for (server in listOf("[]", """[{"ip":"attacker.test","ports":[443]}]""", """[{"ip":"77.111.244.26","ports":[0]}]""")) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeOperaProfiles.content("EU", "device", "token", JsonParser.parseString(server).asJsonArray)
            }
        }
    }

    @Test fun digestIncludesPostUriQueryAndAuthQop() {
        val request = Request.Builder().url("https://api2.sec-tunnel.com/v4/discover?a=b")
            .post(okhttp3.FormBody.Builder().build()).build()
        val header = OperaDigest.authorization("Digest realm=\"api\", nonce=\"nonce\", qop=\"auth,auth-int\", opaque=\"opaque\"", request, "client")!!
        val ha1 = OperaDigest.hash("MD5", "se0316:api:SILrMEPBmJuhomxWkfm3JalqHX2Eheg1YhlEZiMh8II")
        val ha2 = OperaDigest.hash("MD5", "POST:/v4/discover?a=b")
        val expected = OperaDigest.hash("MD5", "$ha1:nonce:00000001:client:auth:$ha2")
        assertTrue(header.contains("response=\"$expected\""))
        assertTrue(header.contains("uri=\"/v4/discover?a=b\""))
        assertTrue(header.contains("opaque=\"opaque\""))
    }

    @Test fun unsafeOrUnsupportedDigestChallengesAreRejected() {
        val request = Request.Builder().url("https://api2.sec-tunnel.com/v4/discover").build()
        for (challenge in listOf("Basic realm=\"api\"", "Digest realm=\"api\"", "Digest realm=\"api\",nonce=\"n\",qop=\"auth-int\"", "Digest realm=\"api\",nonce=\"n\",algorithm=SHA-256", "Digest realm=\"api\",nonce=\"bad\nnonce\"")) {
            assertNull(OperaDigest.authorization(challenge, request))
        }
    }

    @Test fun registrationUsesFixedApiAndProducesIndependentDeviceCredentials() {
        val requests = mutableListOf<Request>()
        val repository = NativeOperaProfilesRepository(Interceptor { chain ->
            val request = chain.request()
            requests.add(request)
            assertEquals("api2.sec-tunnel.com", request.url.host)
            assertEquals("https", request.url.scheme)
            assertEquals("POST", request.method)
            val payload = when (request.url.encodedPath) {
                "/v4/register_subscriber" -> "null"
                "/v4/register_device" -> """{"device_id":"test-device","device_password":"temporary-secret"}"""
                "/v4/discover" -> """{"ips":[{"ip":"77.111.244.26","ports":[443]}]}"""
                else -> error("Unexpected endpoint")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"return_code":{"0":"OK"},"data":$payload}""".toResponseBody("application/json".toMediaType())).build()
        })
        val result = JsonParser.parseString(repository.generate("EU")).asJsonObject
        assertEquals(3, requests.size)
        val discovery = requests.last().body as okhttp3.FormBody
        assertEquals("serial_no", discovery.name(0))
        assertEquals(OperaDigest.hash("SHA-1", "test-device").uppercase(), discovery.value(0))
        assertEquals("\"EU\",,", discovery.value(1))
        assertEquals("temporary-secret", result.getAsJsonArray("outbounds")[0].asJsonObject.get("password").asString)
    }

    @Test fun apiFailuresExposeNoCredentialsOrResponseText() {
        val repository = NativeOperaProfilesRepository(Interceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"return_code":{"7":"secret-token"},"data":null}""".toResponseBody()).build()
        })
        val failure = assertThrows(NativeOperaUnavailableException::class.java) { repository.generate("AS") }
        assertFalse(failure.message!!.contains("secret-token"))
        assertNull(failure.cause)
    }

    @Test fun cancelledGenerationDoesNotContactApi() {
        val cancellation = NativeProfileImportCancellation().apply { cancel() }
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            NativeOperaProfilesRepository().generate("EU", cancellation)
        }
    }
}
