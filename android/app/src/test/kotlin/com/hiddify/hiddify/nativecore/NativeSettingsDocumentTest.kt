package com.hiddify.hiddify.nativecore

import com.hiddify.hiddify.nativeprofile.NativeJsonDocument
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class NativeSettingsDocumentTest {
    @Test fun enabledClashApiRejectsZeroPortButDisabledApiMayStoreZero() {
        assertTrue(runCatching { NativeSettingsDocument.merge(JsonObject(), json("""{"enable-clash-api":true,"clash-api-port":0}""")) }.isFailure)
        NativeSettingsDocument.merge(JsonObject(), json("""{"enable-clash-api":false,"clash-api-port":0}"""))
    }
    private fun json(text: String) = NativeJsonDocument.parse(text).asJsonObject
    private fun rejected(key: String, value: String) {
        assertTrue("$key=$value", runCatching { NativeSettingsDocument.merge(JsonObject(), json("{\"$key\":$value}")) }.isFailure)
    }
    @Test fun importedTypesAndBoundsAreCheckedBeforeMutation() {
        NativeSettingsDocument.booleanKeys.forEach { key -> listOf("null", "0", "1", "\"true\"", "[]", "{}").forEach { rejected(key, it) } }
        NativeSettingsDocument.portKeys.forEach { key -> listOf("null", "true", "\"443\"", "[]", "{}", "-1", "65536", "1.5", "9223372036854775808").forEach { rejected(key, it) } }
        NativeSettingsDocument.choices.keys.forEach { key -> listOf("null", "true", "0", "[]", "{}", "\"invalid-choice\"").forEach { rejected(key, it) } }
        NativeSettingsDocument.stringKeys.forEach { key -> listOf("null", "true", "0", "[]", "{}").forEach { rejected(key, it) } }
        NativeSettingsDocument.objectKeys.forEach { key -> listOf("null", "true", "0", "[]", "\"{}\"").forEach { rejected(key, it) } }
        // This is a matrix of more than 200 invalid inputs, not a count of independent defects.
    }
    @Test fun partialImportRetainsSiblingSecretsAndUnknownCurrentFields() {
        val original = json("""{"tls-tricks":{"enable-padding":true,"padding-size":"10"},"extra-security":{"mode":"warp","warp":{"license-key":"secret"}},"future-option":42}""")
        val result = NativeSettingsDocument.merge(original, json("""{"tls-tricks":{"enable-fragment":true},"extra-security":{"warp":{"clean-port":1234}}}"""))
        assertTrue(result.getAsJsonObject("tls-tricks").get("enable-padding").asBoolean)
        assertEquals("secret", result.getAsJsonObject("extra-security").getAsJsonObject("warp").get("license-key").asString)
        assertEquals(42, result.get("future-option").asInt)
        assertFalse(original.getAsJsonObject("tls-tricks").has("enable-fragment"))
    }
    @Test fun invalidImportLeavesOriginalUntouched() {
        val original = json("""{"mixed-port":12334,"tls-tricks":{"enable-padding":true}}""")
        assertTrue(runCatching { NativeSettingsDocument.merge(original, json("""{"mixed-port":12337}""")) }.isFailure)
        assertEquals(12334, original.get("mixed-port").asInt)
        assertTrue(runCatching { NativeSettingsDocument.merge(original, json("""{"tls-tricks":{"enable-padding":"true"}}""")) }.isFailure)
    }
    @Test fun checksIpv6MinimumAndNestedRangesWithoutRestrictingResolverSchemes() {
        assertTrue(runCatching { NativeSettingsDocument.merge(JsonObject(), json("""{"ipv6-mode":"prefer_ipv6","mtu":576}""")) }.isFailure)
        rejected("tls-tricks", """{"fragment-size":"20-1"}""")
        rejected("tls-tricks", """{"fragment-sleep":null}""")
        val value = NativeSettingsDocument.merge(JsonObject(), json("""{"direct-dns-address":"local","tls-tricks":{"fragment-size":""}}"""))
        assertEquals("local", value.get("direct-dns-address").asString)
    }
}
