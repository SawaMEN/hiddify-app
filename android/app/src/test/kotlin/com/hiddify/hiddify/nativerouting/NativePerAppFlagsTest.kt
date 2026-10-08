package com.hiddify.hiddify.nativerouting

import org.junit.Assert.*
import org.junit.Test

class NativePerAppFlagsTest {
    @Test fun manualSelectionStillTogglesInTwoClicks() {
        assertEquals(1, NativePerAppFlags.toggle(0))
        assertEquals(0, NativePerAppFlags.toggle(1))
    }
    @Test fun preservesDartAutomaticManualForcedCycle() {
        var flags = 4
        assertNull(NativePerAppFlags.checkboxValue(flags))
        assertTrue(NativePerAppFlags.selected(flags))
        flags = NativePerAppFlags.toggle(flags)
        assertEquals(5, flags)
        assertEquals(true, NativePerAppFlags.checkboxValue(flags))
        flags = NativePerAppFlags.toggle(flags)
        assertEquals(6, flags)
        assertEquals(false, NativePerAppFlags.checkboxValue(flags))
        assertFalse(NativePerAppFlags.selected(flags))
        assertEquals(4, NativePerAppFlags.toggle(flags))
    }
    @Test fun legacyManualForcedEntryIsRemovedAsInDartDao() {
        assertFalse(NativePerAppFlags.selected(2))
        assertEquals(0, NativePerAppFlags.toggle(2))
    }
    @Test fun manualChangesNeverDiscardAutomaticPolicy() {
        assertEquals(5, NativePerAppFlags.restoreManual(6, true))
        assertEquals(6, NativePerAppFlags.restoreManual(5, false))
        assertEquals(13, NativePerAppFlags.restoreManual(14, true))
    }
    @Test fun sourcePrioritiesDistinguishManualAutoAndForcedSelections() {
        assertEquals(listOf(1, 2, 3, 4), listOf(5, 4, 6, null).map(NativePerAppFlags::priority))
    }
}
