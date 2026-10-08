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

    @Test fun unavailableChainStagesCannotSilentlyConnectWithoutTheRequestedStage() {
        for (mode in listOf("psiphon", "profile")) {
            val error = runCatching { project("""{"chain-status":"extra_security","extra-security":{"mode":"$mode"}}""") }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertTrue(error!!.message!!.contains(mode))
        }
    }

    @Test fun explicitOffDisablesLegacyWarpButAbsentChainSettingPreservesIt() {
        assertFalse(project("""{"chain-status":"off","warp":{"enable":true}}""").getAsJsonObject("warp").get("enable").asBoolean)
        assertTrue(project("""{"warp":{"enable":true}}""").getAsJsonObject("warp").get("enable").asBoolean)
    }
}
