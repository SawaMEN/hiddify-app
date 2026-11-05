package com.hiddify.hiddify.nativecore

import com.hiddify.hiddify.nativeprofile.NativeJsonDocument
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class NativeCoreOptionsProjectionTest {
    @Test fun deletingTheActiveChainProfileDisablesTheBrokenStage() {
        val original = """{"chain-status":"extra_security","extra-security":{"mode":"profile","profile":{"id":"chain"}},
            "unblocker":{"mode":"warp","profile":{"id":"chain"}},"mixed-port":12334}"""
        val root = JsonParser.parseString(NativeCoreOptionsProjection.removeDeletedChainProfile(original, "chain")).asJsonObject
        assertEquals("off", root.get("chain-status").asString)
        assertTrue(root.getAsJsonObject("extra-security").getAsJsonObject("profile").get("id").isJsonNull)
        assertTrue(root.getAsJsonObject("unblocker").getAsJsonObject("profile").get("id").isJsonNull)
        assertEquals(12334, root.get("mixed-port").asInt)
        assertEquals(original, NativeCoreOptionsProjection.removeDeletedChainProfile(original, "unrelated"))
    }

    @Test fun deletingAnUnusedProfileReferenceKeepsWarpActive() {
        val root = JsonParser.parseString(NativeCoreOptionsProjection.removeDeletedChainProfile(
            """{"chain-status":"unblocker","unblocker":{"mode":"warp","profile":{"id":"chain"}}}""", "chain")).asJsonObject
        assertEquals("unblocker", root.get("chain-status").asString)
    }

    @Test fun chainSelectionRejectsMissingAndMainProfilesBeforeSaving() {
        fun config(id: String?) = """{"chain-status":"extra_security","extra-security":{"mode":"profile","profile":{"id":${id?.let { "\"$it\"" } ?: "null"}}}}"""
        for (id in listOf(null, "missing", "main")) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeCoreOptionsProjection.validateChainSelection(config(id), "main", setOf("main", "chain"))
            }
        }
        NativeCoreOptionsProjection.validateChainSelection(config("chain"), "main", setOf("main", "chain"))
    }
    private fun project(json: String) = NativeJsonDocument.parse(NativeCoreOptionsProjection.apply(json)).asJsonObject

    @Test fun coreReceivesTheDefaultsShownByNativeEditors() {
        val root = project("{}")
        assertEquals(NativeDnsOptions().remoteAddress, root.get("remote-dns-address").asString)
        assertEquals(NativeDnsOptions().directAddress, root.get("direct-dns-address").asString)
        assertEquals(NativeGeneralOptions().testUrl, root.get("connection-test-url").asString)
        assertEquals("sing15", root.get("tun-implementation").asString)
        assertEquals("ipv4_only", root.get("ipv6-mode").asString)
        assertEquals(0, root.get("direct-port").asInt)
        assertEquals(12338, root.get("lan-sharing-port").asInt)
        assertFalse(root.get("local-proxy-auth").asBoolean)
        assertFalse(root.get("local-proxy-in-vpn").asBoolean)
        assertFalse(root.get("use-xray-core-when-possible").asBoolean)
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

    @Test fun warpStagesUseModernCoreTunnelModesAndPreserveDirection() {
        for (stage in listOf("extra-security" to "extra_security", "unblocker" to "unblocker")) {
            for (tunnel in NativeConfigChoices.warpTunnelChoices) {
                val root = project("""{"chain-status":"${stage.second}","warp":{"enable":true},
                    "${stage.first}":{"mode":"warp","warp":{"tunnel":"$tunnel","license-key":"key","mtu":1300}}}""")
                val hop = root.getAsJsonObject(stage.first)
                assertEquals(stage.second, root.get("chain-status").asString)
                assertEquals(tunnel, hop.get("mode").asString)
                assertEquals("key", hop.getAsJsonObject("warp").get("license-key").asString)
                assertEquals(1300, hop.getAsJsonObject("warp").get("mtu").asInt)
                assertFalse(hop.getAsJsonObject("warp").has("tunnel"))
                assertFalse(root.getAsJsonObject("warp").get("enable").asBoolean)
            }
        }
        assertEquals("warp_auto", project("""{"chain-status":"unblocker","unblocker":{"mode":"warp"}}""")
            .getAsJsonObject("unblocker").get("mode").asString)
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

    @Test fun disabledWarpPayloadsAreOmittedBeforeTheCoreDecodesThem() {
        val inputs = listOf(
            """{"warp":{"enable":false,"wireguard-config":"naive://not-json"},"warp2":{"wireguard-config":"nan"}}""",
            """{"chain-status":"off","warp":{"enable":true,"wireguard-config":"naive://not-json"},
                "warp2":{"enable":true,"wireguard-config":"nan"}}""",
            """{"chain-status":"unblocker","unblocker":{"mode":"psiphon"},
                "warp":{"enable":true,"wireguard-config":"naive://not-json"},
                "warp2":{"enable":true,"wireguard-config":"nan"}}""",
        )
        for (input in inputs) {
            val root = project(input)
            for (key in listOf("warp", "warp2")) {
                val warp = root.getAsJsonObject(key)
                assertFalse("unused $key must be disabled", warp.get("enable")?.asBoolean == true)
                assertFalse("unused $key must not be decoded as JSON by the core", warp.has("wireguard-config"))
            }
        }
    }

    @Test fun enabledWarpPayloadsArePreservedWhileInactiveSecondWarpIsSanitized() {
        val original = """{"private_key":"keep"}"""
        val root = project("""{"warp":{"enable":true,"wireguard-config":"{\"private_key\":\"keep\"}"},
            "warp2":{"enable":false,"wireguard-config":"naive://not-json"}}""")
        assertEquals(original, root.getAsJsonObject("warp").get("wireguard-config").asString)
        assertFalse(root.getAsJsonObject("warp2").has("wireguard-config"))

        val chain = project("""{"chain-status":"extra_security","extra-security":{"mode":"warp"},
            "warp":{"wireguard-config":"{\"private_key\":\"keep\"}"},"warp2":{"wireguard-config":"invalid"}}""")
        assertEquals("warp_auto", chain.getAsJsonObject("extra-security").get("mode").asString)
        assertFalse(chain.getAsJsonObject("warp").get("enable").asBoolean)
        assertFalse(chain.getAsJsonObject("warp").has("wireguard-config"))
        assertFalse(chain.getAsJsonObject("warp2").has("wireguard-config"))
    }

    @Test fun malformedActiveWarpJsonCannotBlockOtherwiseValidCoreSettings() {
        for (invalid in listOf("nan", "naive://obsolete", "{broken", "[]", "42", "\"\"", "true")) {
            val payload = com.google.gson.JsonPrimitive(invalid).toString()
            val settings = """{"warp":{"enable":true,"id":"existing-license","wireguard-config":$payload},"warp2":{"enable":false}}"""
            val root = project(settings)
            val warp = root.getAsJsonObject("warp")
            assertTrue("WARP must stay enabled for $invalid", warp.get("enable").asBoolean)
            assertEquals("existing-license", warp.get("id").asString)
            assertFalse("Unparseable credentials must not be sent to Go: $invalid", warp.has("wireguard-config"))
        }
    }

    @Test fun validActiveWarpJsonAndOriginalOptionsRemainUntouched() {
        val settings = """{"warp":{"enable":true,"wireguard-config":"{\"private_key\":\"keep\"}"},
            "warp2":{"enable":true,"wireguard-config":"null"}}"""
        val root = project(settings)
        assertEquals("""{"private_key":"keep"}""", root.getAsJsonObject("warp").get("wireguard-config").asString)
        assertEquals("null", root.getAsJsonObject("warp2").get("wireguard-config").asString)
        assertTrue(settings.contains("wireguard-config"))
    }

    @Test fun importedProfileValidationDoesNotForwardLegacyWarpPayloads() {
        val root = NativeJsonDocument.parse(NativeCoreOptionsProjection.forProfileValidation(
            """{"warp":{"enable":true,"wireguard-config":"naive://not-json"}}""",
        )).asJsonObject
        assertFalse(root.getAsJsonObject("warp").get("enable").asBoolean)
        assertFalse(root.getAsJsonObject("warp").has("wireguard-config"))
    }

    @Test fun explicitOffDisablesLegacyWarpButAbsentChainSettingPreservesIt() {
        assertFalse(project("""{"chain-status":"off","warp":{"enable":true}}""").getAsJsonObject("warp").get("enable").asBoolean)
        assertTrue(project("""{"warp":{"enable":true}}""").getAsJsonObject("warp").get("enable").asBoolean)
    }
}
