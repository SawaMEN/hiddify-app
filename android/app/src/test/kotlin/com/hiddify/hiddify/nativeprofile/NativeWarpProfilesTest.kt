package com.hiddify.hiddify.nativeprofile

import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import java.util.concurrent.CancellationException

class NativeWarpProfilesTest {
    private val conf = """
        [Interface]
        PrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
        Address = 172.16.0.2/32, 2606:4700:110:1234::2/128
        MTU = 1280
        Jc = 4
        Jmin = 40
        Jmax = 70
        S1 = 0
        S2 = 0
        H1 = 1
        H2 = 2
        H3 = 3
        H4 = 4
        I1 = <b 0x01020304><r 16>
        [Peer]
        PublicKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
        Endpoint = [2001:db8::1]:2408
        AllowedIPs = 0.0.0.0/0, ::/0
        PersistentKeepalive = 25
    """.trimIndent()

    private fun envelope(source: Int, content: String = conf): String = when (source) {
        0 -> JsonObject().apply {
            addProperty("success", true)
            add("content", JsonObject().apply { addProperty("configBase64", encoded(content)) })
        }.toString()
        1 -> content
        2 -> JsonObject().apply { addProperty("config", content) }.toString()
        3 -> JsonObject().apply { addProperty("content", encoded(content)) }.toString()
        else -> error("Unexpected source")
    }
    private fun encoded(value: String) = Base64.getEncoder().encodeToString(value.toByteArray())
    private fun decoded(value: String) = String(Base64.getUrlDecoder().decode(value.removePrefix("vpn://")))

    @Test fun builtinsRemainAvailableInEveryRegionAndDoNotExposeGeneratorUrlsAsSubscriptions() {
        assertEquals(3, NativeWarpProfiles.profiles.size)
        for (profile in NativeWarpProfiles.profiles) {
            assertTrue(profile.matches("ru")); assertTrue(profile.matches("other")); assertTrue(profile.matches("ir"))
            assertNotNull(profile.warpMode)
            assertTrue(profile.url.startsWith("builtin:"))
            assertTrue(profile.consent(true).isNotBlank())
        }
        val remote = NativeFreeProfiles.parse("""{"profiles":[{"region":[],"sublink":"https://example.com/sub","title":{"en":"Provider"},"tags":{"en":[]},"consent":{"en":"Consent"},"warpMode":"GENERATED_AWG"}]}""").single()
        assertNull(remote.warpMode)
    }

    @Test fun automaticAwgUsesWireguardCompatibleHeadersAndCacheIdentityWithoutCredentials() {
        val ordinary = NativeJsonDocument.parse(NativeWarpProfiles.content(NativeWarpMode.STANDARD)).asJsonObject.getAsJsonArray("endpoints")[0].asJsonObject
        assertFalse(ordinary.has("awg"))
        val awg = NativeJsonDocument.parse(NativeWarpProfiles.content(NativeWarpMode.AWG)).asJsonObject.getAsJsonArray("endpoints")[0].asJsonObject
        assertEquals("warp", awg.get("type").asString)
        assertNotEquals(ordinary.get("unique_identifier"), awg.get("unique_identifier"))
        assertFalse(awg.has("private_key")); assertFalse(awg.has("profile"))
        val options = awg.getAsJsonObject("awg")
        for (i in 1..4) {
            assertEquals(0, options.get("s$i").asInt)
            assertEquals(i.toString(), options.get("h$i").asString)
        }
        assertTrue(options.get("jc").asInt > 0)
        val summary = NativeImportSummary.parse(NativeWarpProfiles.content(NativeWarpMode.AWG))
        assertEquals(1, summary.servers)
        assertEquals(0, summary.unknownTypes)
    }

    @Test fun allFourEnvelopesPreserveKeysIpv6RoutesAndSignaturePackets() {
        for (source in 0..3) {
            val link = NativeWarpProfiles.generatedContent(envelope(source), source)
            assertEquals(conf, decoded(link))
            assertEquals(1, NativeImportSummary.parse(link).servers)
            assertEquals(0, NativeImportSummary.parse(link).unknownTypes)
        }
    }

    @Test fun rejectsHtmlUrlsMissingKeysMalformedEnvelopesAndPlainWireguard() {
        val invalid = listOf("<html>Error</html>", "https://example.com/config", "{}", conf.replace("PrivateKey =", "MissingKey ="),
            conf.replace("Jc =", "Other =").replace("Jmin =", "OtherMin =").replace("Jmax =", "OtherMax =").replace("I1 =", "OtherSignature ="))
        for (content in invalid) for (source in 0..3) {
            assertTrue(runCatching { NativeWarpProfiles.generatedContent(envelope(source, content), source) }.isFailure)
        }
        for (content in listOf("""{"success":false,"content":{}}""", """{"success":"true","content":{"configBase64":"bad"}}""")) {
            assertTrue(runCatching { NativeWarpProfiles.generatedContent(content, 0) }.isFailure)
        }
    }

    private fun repository(reply: (Int) -> Pair<Int, String>): NativeWarpProfilesRepository {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val index = chain.request().url.encodedPath.removePrefix("/").toInt()
            val (code, body) = reply(index)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
                .body(body.toResponseBody()).build()
        }.build()
        return NativeWarpProfilesRepository(http, (0..3).map { Request.Builder().url("https://example.com/$it").build() })
    }

    @Test fun unavailableMalformedAndCoreRejectedSourcesFallBackWithoutSaving() {
        val attempts = mutableListOf<Int>()
        var validations = 0
        val result = repository { source ->
            attempts += source
            when (source) {
                0 -> 503 to "unavailable"
                1 -> 200 to "<html>error</html>"
                else -> 200 to envelope(source)
            }
        }.generate(NativeProfileImportCancellation()) { content ->
            validations++
            if (validations == 1) error("Core rejected candidate")
            decoded(content)
        }
        assertEquals(conf, result)
        assertEquals(listOf(0, 1, 2, 3), attempts)
        assertEquals(2, validations)
    }

    @Test fun exhaustedSourcesReturnCredentialFreeError() {
        val error = runCatching { repository { 200 to "secret invalid body" }.generate(NativeProfileImportCancellation()) { it } }.exceptionOrNull()
        assertTrue(error is NativeWarpGenerationException)
        assertFalse(error!!.message!!.contains("secret"))
    }

    @Test fun cancellationDuringDownloadOrValidationNeverTriesAnotherSource() {
        for (cancelInDownload in listOf(true, false)) {
            val cancellation = NativeProfileImportCancellation()
            val attempts = mutableListOf<Int>()
            val error = runCatching {
                repository { source ->
                    attempts += source
                    if (cancelInDownload) cancellation.cancel()
                    200 to envelope(source)
                }.generate(cancellation) {
                    cancellation.cancel()
                    cancellation.ensureActive()
                    it
                }
            }.exceptionOrNull()
            assertTrue(error is CancellationException)
            assertEquals(listOf(0), attempts)
        }
    }
}
