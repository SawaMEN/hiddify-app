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
        assertEquals(1, NativeOperaProfiles.profiles.size)
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
            assertTrue(tls.get("disable_sni").asBoolean)
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
        for (challenge in listOf("Basic realm=\"api\"", "Digest realm=\"api\"", "Digest realm=\"api\",nonce=\"n\",qop=\"auth-int\"", "Digest realm=\"api\",nonce=\"n\",algorithm=SHA-1", "Digest realm=\"api\",nonce=\"bad\nnonce\"")) {
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
        assertEquals(5, requests.size)
        assertEquals(3, result.getAsJsonArray("outbounds").size())
        val discovery = requests.last().body as okhttp3.FormBody
        assertEquals("serial_no", discovery.name(0))
        assertEquals(OperaDigest.hash("SHA-1", "test-device").uppercase(), discovery.value(0))
        assertEquals("\"AM\",,", discovery.value(1))
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

    private fun previousProfile(): String = NativeOperaProfiles.content(
        NativeOperaProfiles.regions.keys.associateWith {
            JsonParser.parseString("""[{"ip":"77.111.244.26","ports":[443]}]""").asJsonArray
        }, "old-device", "old-password")

    private fun unavailableDiscovery() = Interceptor { chain ->
        val request = chain.request()
        val text = when (request.url.encodedPath) {
            "/v4/register_subscriber" -> """{"return_code":{"0":"OK"},"data":null}"""
            "/v4/register_device" -> """{"return_code":{"0":"OK"},"data":{"device_id":"device","device_password":"temporary"}}"""
            else -> """{"return_code":{"801":"unavailable"},"data":null}"""
        }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(text.toResponseBody()).build()
    }

    @Test fun discoveryFailureReusesRealCachedIpsWithFreshCredentials() {
        val result = JsonParser.parseString(NativeOperaProfilesRepository(unavailableDiscovery())
            .generate(previousContent = previousProfile())).asJsonObject.getAsJsonArray("outbounds")
        assertEquals(3, result.size())
        assertTrue(result.all { it.asJsonObject.get("server").asString == "77.111.244.26" })
        assertTrue(result.all { it.asJsonObject.get("password").asString == "temporary" })
        assertTrue(result.all { it.asJsonObject.get("username").asString != "old-device" })
    }

    @Test fun initialDiscoveryFailureCannotPublishInventedHostnames() {
        val failure = assertThrows(NativeOperaUnavailableException::class.java) {
            NativeOperaProfilesRepository(unavailableDiscovery()).generate()
        }
        assertEquals("discover-EU", failure.stage)
        assertEquals(801, failure.status)
        assertTrue(failure.message!!.contains("Europe"))
    }

    @Test fun transientAsiaFailureIsRetriedWithoutDroppingItsServers() {
        var asiaAttempts = 0
        val repository = NativeOperaProfilesRepository(Interceptor { chain ->
            val request = chain.request()
            val text = when (request.url.encodedPath) {
                "/v4/register_subscriber" -> """{"return_code":{"0":"OK"},"data":null}"""
                "/v4/register_device" -> """{"return_code":{"0":"OK"},"data":{"device_id":"device","device_password":"temporary"}}"""
                else -> {
                    val form = request.body as okhttp3.FormBody
                    if (form.value(1).contains("AS") && ++asiaAttempts == 1)
                        """{"return_code":{"801":"temporarily unavailable"},"data":null}"""
                    else """{"return_code":{"0":"OK"},"data":{"ips":[{"ip":"77.111.244.26","ports":[443]}]}}"""
                }
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(text.toResponseBody()).build()
        })
        val result = JsonParser.parseString(repository.generate()).asJsonObject.getAsJsonArray("outbounds")
        assertEquals(2, asiaAttempts)
        assertEquals(3, result.size())
        assertTrue(result.any { it.asJsonObject.get("tag").asString.contains("Asia") })
    }

    @Test fun cacheRejectsOldDnsAliasesPrivateIpsAndDisabledCertificateChecks() {
        val root = JsonParser.parseString(previousProfile()).asJsonObject
        val nodes = root.getAsJsonArray("outbounds")
        nodes[0].asJsonObject.addProperty("server", "eu0.sec-tunnel.com")
        nodes[1].asJsonObject.addProperty("server", "127.0.0.1")
        nodes[2].asJsonObject.getAsJsonObject("tls").addProperty("insecure", true)
        NativeOperaProfiles.regions.keys.forEach {
            assertEquals(0, NativeOperaProfiles.cachedServers(root.toString(), it).size())
        }
    }

    @Test fun httpsPortIsPreferredWhenApiListsMultiplePorts() {
        val servers = JsonParser.parseString("""[{"ip":"77.111.244.26","ports":[80,443]}]""").asJsonArray
        val root = JsonParser.parseString(NativeOperaProfiles.content("EU", "device", "token", servers)).asJsonObject
        assertEquals(443, root.getAsJsonArray("outbounds")[0].asJsonObject.get("server_port").asInt)
    }

    @Test fun errorsIdentifyStageAndStatusInOneLanguage() {
        val repository = NativeOperaProfilesRepository(Interceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(405).message("Blocked")
                .body("secret-body".toResponseBody()).build()
        }, russian = true)
        val failure = assertThrows(NativeOperaUnavailableException::class.java) { repository.generate() }
        assertEquals("register_subscriber", failure.stage)
        assertEquals(405, failure.status)
        assertTrue(failure.message!!.contains("регистрация"))
        assertTrue(failure.message!!.contains("HTTP 405"))
        assertFalse(failure.message!!.contains("secret-body"))
        assertFalse(failure.message!!.contains("unavailable"))
    }

    @Test fun unifiedConfigContainsStableRegionLabelsAndRejectsPrivateEndpoints() {
        val choices = NativeOperaProfiles.regions.keys.associateWith {
            JsonParser.parseString("""[{"ip":"77.111.244.26","ports":[443]}]""").asJsonArray
        }
        val outbounds = JsonParser.parseString(NativeOperaProfiles.content(choices, "device", "access")).asJsonObject.getAsJsonArray("outbounds")
        assertEquals(3, outbounds.size())
        assertTrue(outbounds.any { it.asJsonObject.get("tag").asString.contains("Europe") })
        assertTrue(outbounds.any { it.asJsonObject.get("tag").asString.contains("Asia") })
        assertTrue(outbounds.any { it.asJsonObject.get("tag").asString.contains("Americas") })
        for (ip in listOf("127.0.0.1", "10.0.0.1", "::1", "169.254.0.1", "224.0.0.1", "999.1.1.1", "example.test")) {
            assertFalse(ip, NativeOperaProfiles.isPublicLiteral(ip))
        }
        assertTrue(NativeOperaProfiles.isPublicLiteral("77.111.244.26"))
        assertEquals("ALL", NativeOperaProfiles.region(NativeOperaProfiles.SOURCE))
        assertEquals("EU", NativeOperaProfiles.region("builtin:opera-EU"))
        assertEquals(4, NativeOperaProfiles.noticePoints(true).size)
    }

    @Test fun digestSupportsSha256SessionChallenges() {
        val request = Request.Builder().url("https://api2.sec-tunnel.com/v4/discover").build()
        val header = OperaDigest.authorization("Digest realm=\"api\", nonce=\"n\", algorithm=sha-256-SESS, qop=\"auth\"", request, "c")!!
        val ha1 = OperaDigest.hash("SHA-256", OperaDigest.hash("SHA-256", "se0316:api:SILrMEPBmJuhomxWkfm3JalqHX2Eheg1YhlEZiMh8II") + ":n:c")
        val ha2 = OperaDigest.hash("SHA-256", "GET:/v4/discover")
        assertTrue(header.contains(OperaDigest.hash("SHA-256", "$ha1:n:00000001:c:auth:$ha2")))
    }

    @Test fun cancelledGenerationDoesNotContactApi() {
        val cancellation = NativeProfileImportCancellation().apply { cancel() }
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            NativeOperaProfilesRepository().generate("EU", cancellation)
        }
    }
}
