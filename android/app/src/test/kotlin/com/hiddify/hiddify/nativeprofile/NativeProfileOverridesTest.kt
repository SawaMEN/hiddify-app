package com.hiddify.hiddify.nativeprofile

import org.junit.Assert.*
import org.junit.Test

class NativeProfileOverridesTest {
    @Test fun nullAndInvalidFlagHeadersCannotCrashProfileStartup() {
        listOf("null", "[]", "{}", "42", "false").forEach { value ->
            val headers = """{"enable-warp":$value,"enable-fragment":$value}"""
            val resolved = NativeProfileOverrides.resolve(headers, null)
            assertFalse(resolved.has("extra-security"))
            assertFalse(resolved.has("tls-tricks"))
        }
    }

    @Test fun textualFlagsAreCaseInsensitiveAndTrimmed() {
        assertEquals("warp", NativeProfileOverrides.resolve("""{"enable-warp":" TRUE "}""", null)
            .getAsJsonObject("extra-security").get("mode").asString)
    }

    @Test fun wrongOverrideShapesCannotReplaceValidCoreDefaults() {
        listOf("null", "[]", "{}", "true", "42", "\" \"").forEach { value ->
            val result = NativeProfileOverrides.apply("""{"remote-dns-address":"local"}""",
                """{"remote-dns-address":$value}""", null)
            assertEquals("local", NativeJsonDocument.parse(result).asJsonObject.get("remote-dns-address").asString)
        }
        listOf("null", "[]", "true", "42").forEach { value ->
            assertFalse(NativeProfileOverrides.resolve("""{"tls-tricks":$value}""", null).has("tls-tricks"))
        }
    }
    @Test fun fragmentFlagPreservesSubscriptionTlsParameters() {
        val headers = """{"tls-tricks":"{\"fragment-size\":\"30-60\",\"enable-padding\":true}","enable-fragment":"true"}"""
        val result = NativeProfileOverrides.resolve(headers, null).getAsJsonObject("tls-tricks")
        assertEquals("30-60", result.get("fragment-size").asString)
        assertTrue(result.get("enable-padding").asBoolean)
        assertTrue(result.get("enable-fragment").asBoolean)
        val disabled = NativeProfileOverrides.resolve(headers, """{"enableFragment":false}""")
            .getAsJsonObject("tls-tricks")
        assertFalse(disabled.get("enable-fragment").asBoolean)
        assertEquals("30-60", disabled.get("fragment-size").asString)
        assertTrue(disabled.get("enable-padding").asBoolean)
    }

    @Test fun explicitFlagsTakePrecedenceOverHeaders() {
        val headers = """{"enable-warp":"true","enable-fragment":"true"}"""
        val enabled = NativeProfileOverrides.resolve(headers, null)
        assertEquals("warp", enabled.getAsJsonObject("extra-security").get("mode").asString)
        val disabled = NativeProfileOverrides.resolve(headers, """{"enableWarp":false,"enableFragment":false}""")
        assertFalse(disabled.has("extra-security"))
        assertFalse(disabled.getAsJsonObject("tls-tricks").get("enable-fragment").asBoolean)
        assertFalse(NativeProfileOverrides.resolve("""{"enable-fragment":"false"}""", null).has("tls-tricks"))
    }

    @Test fun psiphonWinsAndUnrelatedHeadersAreExcluded() {
        val result = NativeProfileOverrides.resolve("""{"enable-warp":true,"enable-psiphon":true,"region":"ru","remote-dns-address":"","direct-dns-address":"local"}""", null)
        assertEquals("psiphon", result.getAsJsonObject("extra-security").get("mode").asString)
        assertEquals("extra_security", result.get("chain-status").asString)
        assertEquals("local", result.get("direct-dns-address").asString)
        assertFalse(result.has("region"))
        assertFalse(result.has("remote-dns-address"))
    }

    @Test fun mergesOnlyEffectiveOptionsAndRetainsNestedSettings() {
        val base = """{"region":"other","extra-security":{"mode":"none","license":"keep"},"tls-tricks":{"padding-size":"10"}}"""
        val merged = NativeJsonDocument.parse(NativeProfileOverrides.apply(base, null,
            """{"enableWarp":true,"enableFragment":true}""")).asJsonObject
        assertEquals("keep", merged.getAsJsonObject("extra-security").get("license").asString)
        assertEquals("warp", merged.getAsJsonObject("extra-security").get("mode").asString)
        assertEquals("10", merged.getAsJsonObject("tls-tricks").get("padding-size").asString)
        assertTrue(merged.getAsJsonObject("tls-tricks").get("enable-fragment").asBoolean)
        assertEquals("none", NativeJsonDocument.parse(base).asJsonObject.getAsJsonObject("extra-security").get("mode").asString)
    }

    @Test fun featureReplacementPreservesMetadataAndMissingListRestoresFallback() {
        val original = """{"name":"Keep","updateInterval":12,"enableWarp":true}"""
        val replaced = NativeProfileOverrides.withFeatures(original, setOf("fragment"))
        val user = NativeJsonDocument.parse(replaced).asJsonObject
        assertEquals("Keep", user.get("name").asString)
        assertEquals(12, user.get("updateInterval").asInt)
        assertFalse(user.get("enableWarp").asBoolean)
        assertTrue(user.get("enableFragment").asBoolean)
        val fallback = NativeJsonDocument.parse(NativeProfileOverrides.withFeatures(replaced, null)).asJsonObject
        assertFalse(fallback.has("enableWarp"))
        assertFalse(fallback.has("enableFragment"))
        assertEquals("Keep", fallback.get("name").asString)
    }
}
