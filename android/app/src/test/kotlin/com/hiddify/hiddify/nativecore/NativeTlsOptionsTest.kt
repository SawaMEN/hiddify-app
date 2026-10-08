package com.hiddify.hiddify.nativecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeTlsOptionsTest {
    @Test fun switchEditsPreserveImportedRangesAndOtherSwitches() {
        val imported = NativeTlsOptions(true, "", "legacy-range", true, false, "0001-0010")
        assertEquals(imported.copy(fragment = false), NativeTlsOptionField.FRAGMENT.applyTo(imported, "false"))
        assertEquals(imported.copy(padding = true), NativeTlsOptionField.PADDING.applyTo(imported, "true"))
        assertEquals(imported.copy(mixedSniCase = false), NativeTlsOptionField.MIXED_SNI_CASE.applyTo(imported, "false"))
    }

    @Test fun editedRangeIsCanonicalAndOtherRangesRemainVerbatim() {
        val imported = NativeTlsOptions(true, "0001-0010", "", true, false, "legacy-range")
        assertEquals(imported.copy(fragmentSleep = "2-8"), NativeTlsOptionField.FRAGMENT_SLEEP.applyTo(imported, " 002-008 "))
        assertEquals(imported.copy(fragmentSize = "0"), NativeTlsOptionField.FRAGMENT_SIZE.applyTo(imported, "0000"))
        assertEquals("2147483647", NativeTlsOptions.normalizeRange("2147483647"))
        assertEquals("2147483647-2147483647", NativeTlsOptions.normalizeRange("2147483647-2147483647"))
    }

    @Test fun invalidRangeNeverProducesAnUpdate() {
        listOf("", " ", "-1", "1-", "1-2-3", "3-2", "1.5", "+1", "1 - 2", "2147483648", "1\n-2").forEach { input ->
            assertThrows(IllegalArgumentException::class.java) {
                NativeTlsOptionField.FRAGMENT_SIZE.applyTo(NativeTlsOptions(), input)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeTlsOptionField.PADDING.applyTo(NativeTlsOptions(), "1")
        }
    }

    @Test fun importedEmptyRangesStayValidWithoutAllowingEmptyEdits() {
        val imported = NativeTlsOptions(fragmentSize = "", fragmentSleep = "", paddingSize = "")
        assertEquals(imported, imported.validated())
        assertEquals("", NativeTlsOptions.normalizeRange(" ", allowEmpty = true))
        assertThrows(IllegalArgumentException::class.java) { NativeTlsOptions.normalizeRange("") }
    }

    @Test fun resetRestoresOnlyTheChosenField() {
        val custom = NativeTlsOptions(true, "100-200", "1", true, true, "10-20")
        val defaults = NativeTlsOptions()
        NativeTlsOptionField.entries.forEach { field ->
            val reset = field.applyTo(custom, field.value(defaults).toString())
            assertEquals(field.value(defaults), field.value(reset))
            NativeTlsOptionField.entries.filter { it != field }.forEach { untouched ->
                assertEquals(untouched.value(custom), untouched.value(reset))
            }
        }
    }
}
