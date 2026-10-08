package com.hiddify.hiddify.nativecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeGeneralOptionsTest {
    @Test fun editingOneFieldPreservesOtherImportedValues() {
        val imported = NativeGeneralOptions(balancer = "sticky-sessions", resolveDestination = true,
            logLevel = "fatal", testUrl = "legacy-invalid-url", intervalSeconds = 17, clashPort = 32100, useXray = true)
        assertEquals(imported.copy(clashPort = 16756), NativeGeneralOptionField.CLASH_PORT.applyTo(imported, "16756"))
        assertEquals(imported.copy(resolveDestination = false), NativeGeneralOptionField.RESOLVE_DESTINATION.applyTo(imported, "false"))
        assertEquals(imported.copy(balancer = "round-robin"), NativeGeneralOptionField.BALANCER.applyTo(imported, "round-robin"))
    }

    @Test fun urlValidationTrimsWithoutRewritingOtherFields() {
        val imported = NativeGeneralOptions(intervalSeconds = 17, logLevel = "fatal")
        assertEquals(imported.copy(testUrl = "https://example.com:8443/ping"),
            NativeGeneralOptionField.TEST_URL.applyTo(imported, "  https://example.com:8443/ping  "))
        listOf("file:///tmp/config", "https://", "https://example.com/a b", "https://[broken", "http://example.com:0", "http://example.com:65536", "https://example.com/\n").forEach {
            // Leading/trailing whitespace is accepted; an embedded control character is rejected.
            val invalid = if (it.endsWith("\n")) "https://example.com/\nx" else it
            assertThrows(IllegalArgumentException::class.java) {
                NativeGeneralOptionField.TEST_URL.applyTo(imported, invalid)
            }
        }
    }

    @Test fun invalidPortIntervalAndBooleanNeverProduceAnUpdate() {
        val defaults = NativeGeneralOptions()
        listOf("0", "65536", "-1", "not-a-number").forEach {
            assertThrows(IllegalArgumentException::class.java) { NativeGeneralOptionField.CLASH_PORT.applyTo(defaults, it) }
        }
        listOf("0", "86401", "-1").forEach {
            assertThrows(IllegalArgumentException::class.java) { NativeGeneralOptionField.INTERVAL.applyTo(defaults, it) }
        }
        assertThrows(IllegalArgumentException::class.java) { NativeGeneralOptionField.USE_XRAY.applyTo(defaults, "yes") }
    }

    @Test fun eachResetUsesItsOwnDefaultAndLeavesOtherFieldsIntact() {
        val custom = NativeGeneralOptions("sticky-sessions", true, "trace", "https://example.com", 17, 32100, true)
        val defaults = NativeGeneralOptions()
        NativeGeneralOptionField.entries.forEach { field ->
            val reset = field.applyTo(custom, field.value(defaults).toString())
            assertEquals(field.value(defaults), field.value(reset))
            NativeGeneralOptionField.entries.filter { it != field }.forEach { untouched ->
                assertEquals(untouched.value(custom), untouched.value(reset))
            }
        }
    }
}
