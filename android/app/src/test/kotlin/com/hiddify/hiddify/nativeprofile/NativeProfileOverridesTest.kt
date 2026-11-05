package com.hiddify.hiddify.nativeprofile

import org.junit.Assert.*
import org.junit.Test

class NativeProfileOverridesTest {
    @Test fun extraSecurityRetainsOwnDnsRegardlessOfWhichLayerEnablesIt() {
        for (own in listOf("off", "extra_security")) {
            val base = """{"chain-status":"$own","remote-dns-address":"https://own.example/dns-query"}"""
            val merged = NativeJsonDocument.parse(NativeProfileOverrides.apply(base,
                """{"remote-dns-address":"https://subscription.example/dns-query"}""",
                """{"enableWarp":true}""")).asJsonObject
            assertEquals("https://own.example/dns-query", merged.get("remote-dns-address").asString)
        }
        val noChain = NativeJsonDocument.parse(NativeProfileOverrides.apply("{}",
            """{"remote-dns-address":"https://subscription.example/dns-query"}""", null)).asJsonObject
        assertEquals("https://subscription.example/dns-query", noChain.get("remote-dns-address").asString)
        val absentDefault = NativeJsonDocument.parse(NativeProfileOverrides.apply("{}",
            """{"remote-dns-address":"https://subscription.example/dns-query","enable-warp":true}""", null)).asJsonObject
        assertFalse(absentDefault.has("remote-dns-address"))
    }

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

    @Test fun explicitProxySelectionOverridesSubscriptionAndPreservesUserMetadata() {
        val headers = """{"keep-top":"10","remote-dns-address":"tcp://1.1.1.1"}"""
        val user = NativeProfileOverrides.withSelection(
            """{"name":"Custom","enableFragment":true}""", 5, listOf("proxy-A", "proxy-B"))
        val kept = NativeProfileOverrides.resolve(headers, user)
        assertEquals(5, kept.get("keep-top").asInt)
        assertEquals(listOf("proxy-A", "proxy-B"), kept.getAsJsonArray("keep-tags").map { it.asString })
        assertEquals("Custom", NativeJsonDocument.parse(user).asJsonObject.get("name").asString)
        assertTrue(NativeJsonDocument.parse(user).asJsonObject.get("enableFragment").asBoolean)
        val reset = NativeProfileOverrides.withSelection(user, -1, emptyList())
        assertEquals(-1, NativeProfileOverrides.resolve(headers, reset).get("keep-top").asInt)
        assertFalse(NativeProfileOverrides.resolve(headers, reset).has("keep-tags"))
    }

    @Test fun selectionRejectsInvalidValuesAndUntrustedTags() {
        for (value in listOf("0", "-2", "abc", "null", "true")) {
            assertFalse(NativeProfileOverrides.selection("""{"keep-top":"$value"}""", null).has("keep-top"))
        }
        assertEquals(3, NativeProfileOverrides.selection("""{"keep-top":"3"}""", null).get("keep-top").asInt)
        assertEquals(500, NativeProfileOverrides.selection("""{"keep-top":"500"}""", null).get("keep-top").asInt)
        assertThrows(IllegalArgumentException::class.java) {
            NativeProfileOverrides.withSelection(null, 0, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeProfileOverrides.withSelection(null, null, listOf("same", "same"))
        }
        val malformed = NativeProfileOverrides.selection(null, """{"keepTags":[false,42,"", "ok", "ok"]}""")
        assertEquals(listOf("ok"), malformed.getAsJsonArray("keep-tags").map { it.asString })
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
