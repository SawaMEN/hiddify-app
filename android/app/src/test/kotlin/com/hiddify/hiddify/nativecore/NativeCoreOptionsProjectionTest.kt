package com.hiddify.hiddify.nativecore

import com.hiddify.hiddify.nativeprofile.NativeJsonDocument
import org.junit.Assert.*
import org.junit.Test

class NativeCoreOptionsProjectionTest {
    private fun project(json: String) = NativeJsonDocument.parse(NativeCoreOptionsProjection.apply(json)).asJsonObject

    @Test fun coreReceivesTheDefaultsShownByNativeEditors() {
        val root = project("{}")
        assertEquals(NativeDnsOptions().remoteAddress, root.get("remote-dns-address").asString)
        assertEquals(NativeDnsOptions().directAddress, root.get("direct-dns-address").asString)
        assertEquals(NativeGeneralOptions().testUrl, root.get("connection-test-url").asString)
        assertEquals("gvisor", root.get("tun-implementation").asString)
        assertEquals(NativeTlsOptions().fragmentSize, root.getAsJsonObject("tls-tricks").get("fragment-size").asString)
    }

    @Test fun explicitValuesAndUnknownOptionsSurviveProjection() {
        val root = project("""{"remote-dns-address":"local","mixed-port":12345,"future-option":42,"tls-tricks":{"fragment-size":"20-40"}}""")
        assertEquals("local", root.get("remote-dns-address").asString)
        assertEquals(12345, root.get("mixed-port").asInt)
        assertEquals(42, root.get("future-option").asInt)
        assertEquals("20-40", root.getAsJsonObject("tls-tricks").get("fragment-size").asString)
    }

    @Test fun disabledListenerFlagsBecomeTheZeroPortsConsumedByCore() {
        for (listener in listOf("mixed", "direct", "tproxy", "redirect")) {
            val root = project("""{"enable-$listener-port":false,"$listener-port":12345}""")
            assertEquals(0, root.get("$listener-port").asInt)
        }
    }

    @Test fun rawConfigurationSwitchUsesThePinnedCoreFieldName() {
        for (enabled in listOf(true, false)) {
            assertEquals(enabled, project("""{"execute-config-as-is":$enabled}""").get("enable-full-config").asBoolean)
        }
    }

    @Test fun warpStagesAreConvertedWithCorrectDirection() {
        val extra = project("""{"chain-status":"extra_security","extra-security":{"mode":"warp","warp":{"license-key":"key","clean-port":443}}}""")
        assertTrue(extra.getAsJsonObject("warp").get("enable").asBoolean)
        assertEquals("warp_over_proxy", extra.getAsJsonObject("warp").get("mode").asString)
        assertEquals("key", extra.getAsJsonObject("warp").get("id").asString)
        assertEquals(443, extra.getAsJsonObject("warp").get("clean-port").asInt)
        val unblocker = project("""{"chain-status":"unblocker","unblocker":{"mode":"warp"}}""")
        assertEquals("proxy_over_warp", unblocker.getAsJsonObject("warp").get("mode").asString)
    }

    @Test fun psiphonStageProjectsRegionAndDirectionWithoutEnablingLegacyWarp() {
        for (direction in listOf("extra_security", "unblocker")) {
            val key = if (direction == "extra_security") "extra-security" else "unblocker"
            val root = project("""{"chain-status":"$direction","warp":{"enable":true},"$key":{"mode":"psiphon","psiphon":{"region":"de","conduit-pairing-id":"pair"}}}""")
            val stage = root.getAsJsonObject("chain-stage")
            assertEquals(direction, stage.get("direction").asString)
            assertEquals("psiphon", stage.get("mode").asString)
            assertEquals("DE", stage.get("region").asString)
            assertEquals("pair", stage.get("conduit-pairing-id").asString)
            assertFalse(root.getAsJsonObject("warp").get("enable").asBoolean)
        }
        assertEquals("", project("""{"chain-status":"unblocker","unblocker":{"mode":"psiphon"}}""").getAsJsonObject("chain-stage").get("region").asString)
    }

    @Test fun chainProfileUsesCurrentLocalContentAndCannotTrustBackupRuntimePayload() {
        var loaded: String? = null
        val root = NativeJsonDocument.parse(NativeCoreOptionsProjection.apply(
            """{"chain-status":"unblocker","unblocker":{"mode":"profile","profile":{"id":"stage-id"}},"chain-stage":{"profile-content":"injected"}}""",
            currentProfileId = "main-id",
            profileContent = { loaded = it; "current file" },
        )).asJsonObject
        assertEquals("stage-id", loaded)
        assertEquals("current file", root.getAsJsonObject("chain-stage").get("profile-content").asString)
        assertFalse(project("""{"chain-status":"off","chain-stage":{"mode":"psiphon"}}""").has("chain-stage"))
    }

    @Test fun chainProfileRejectsMissingSelfAndUnsafeIdsBeforeFileAccess() {
        for (id in listOf("", "main-id", "../escape")) {
            var loaded = false
            val error = runCatching {
                NativeCoreOptionsProjection.apply("""{"chain-status":"unblocker","unblocker":{"mode":"profile","profile":{"id":"$id"}}}""", "main-id") { loaded = true; "file" }
            }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertFalse(loaded)
        }
    }

    @Test fun deletedOrUnreadableChainProfileDoesNotFallBackToPlainConnection() {
        val error = runCatching {
            NativeCoreOptionsProjection.apply("""{"chain-status":"unblocker","unblocker":{"mode":"profile","profile":{"id":"deleted"}}}""", "main-id") { error("Chain profile was deleted") }
        }.exceptionOrNull()
        assertTrue(error!!.message!!.contains("deleted"))
    }

    @Test fun largeEscapedChainPayloadIsRejectedBeforeRpc() {
        val error = runCatching {
            NativeCoreOptionsProjection.apply("""{"chain-status":"unblocker","unblocker":{"mode":"profile","profile":{"id":"stage"}}}""", "main-id") { "\"".repeat(2 * 1024 * 1024) }
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("message limit"))
    }

    @Test fun importingSourceDoesNotRequireOrResolveTheCurrentlySelectedChain() {
        val root = NativeJsonDocument.parse(NativeCoreOptionsProjection.forProfileValidation(
            """{"chain-status":"unblocker","unblocker":{"mode":"profile","profile":{"id":"main-id"}}}""",
        )).asJsonObject
        assertEquals("off", root.get("chain-status").asString)
        assertFalse(root.has("chain-stage"))
    }

    @Test fun explicitOffDisablesLegacyWarpButAbsentChainSettingPreservesIt() {
        assertFalse(project("""{"chain-status":"off","warp":{"enable":true}}""").getAsJsonObject("warp").get("enable").asBoolean)
        assertTrue(project("""{"warp":{"enable":true}}""").getAsJsonObject("warp").get("enable").asBoolean)
    }
}
