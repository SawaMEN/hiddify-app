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
        PrivateKey = AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=
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
        PublicKey = AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=
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
    private fun endpoint(value: String) = NativeJsonDocument.parse(value).asJsonObject.getAsJsonArray("endpoints")[0].asJsonObject

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

    @Test fun onlyUnmodifiedLegacyBuiltinsAreRepaired() {
        for (mode in listOf(NativeWarpMode.STANDARD, NativeWarpMode.AWG)) {
            val raw = NativeWarpProfiles.legacyContent(mode)
            assertEquals(mode, NativeWarpProfiles.legacyMode(raw))
            assertNull(NativeWarpProfiles.legacyMode(raw.replace("1280", "1400")))
        }
        assertNull(NativeWarpProfiles.legacyMode("{}"))
        assertNull(NativeWarpProfiles.legacyMode(NativeWarpAccount.parse(conf).content(NativeWarpMode.AWG)))
    }

    @Test fun allFourEnvelopesPreserveKeysIpv6AndSignaturePackets() {
        for (source in 0..3) {
            val content = NativeWarpAccount.parse(envelope(source)).content(NativeWarpMode.GENERATED_AWG)
            val node = endpoint(content)
            assertEquals("awg", node.get("type").asString)
            assertEquals("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=", node.get("private_key").asString)
            assertEquals(2, node.getAsJsonArray("address").size())
            assertEquals("<b 0x01020304><r 16>", node.getAsJsonObject("awg").get("i1").asString)
            assertEquals(25, node.getAsJsonArray("peers")[0].asJsonObject.get("persistent_keepalive_interval").asInt)
            assertEquals(1, NativeImportSummary.parse(content).servers)
            assertEquals(0, NativeImportSummary.parse(content).unknownTypes)
        }
    }

    @Test fun rejectsInvalidResponsesAndServerDependentAwgParameters() {
        val invalid = listOf("<html>Error</html>", "https://example.com/config", "{}", conf.replace("PrivateKey =", "MissingKey ="),
            conf.replace("S1 = 0", "S1 = 20"), conf.replace("H1 = 1", "H1 = 123"),
            conf.replace("[Peer]", "HeaderProtectionKey = secret\n[Peer]"), conf.replace("[Peer]", "[PostUp]"),
            conf.replace("[2001:db8::1]", "example.com"), conf.replace("[2001:db8::1]", "abcdef"))
        for (content in invalid) for (source in 0..3) assertTrue(runCatching { NativeWarpAccount.parse(envelope(source, content)) }.isFailure)
        for (content in listOf("""{"success":false,"content":{}}""", """{"success":"true","content":{"configBase64":"bad"}}""")) {
            assertTrue(runCatching { NativeWarpAccount.parse(content) }.isFailure)
        }
    }

    @Test fun addsSignatureToDesktopGeneratorConfigsAndKeepsWireguardHeaders() {
        val desktop = conf.lineSequence().filterNot { it.startsWith("I1") }.joinToString("\n")
        val node = endpoint(NativeWarpAccount.parse(desktop).content(NativeWarpMode.AWG))
        val options = node.getAsJsonObject("awg")
        assertTrue(options.get("i1").asString.startsWith("<b 0xc2"))
        assertEquals(1280, node.get("mtu").asInt)
        assertFalse(node.get("useIntegratedTun").asBoolean)
        for (i in 1..4) { assertEquals(0, options.get("s$i").asInt); assertEquals(i.toString(), options.get("h$i").asString) }
        val ordinary = endpoint(NativeWarpAccount.parse(desktop).content(NativeWarpMode.STANDARD))
        assertEquals("wireguard", ordinary.get("type").asString)
        assertFalse(ordinary.has("awg"))
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
            content
        }
        assertEquals("awg", endpoint(result).get("type").asString)
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
