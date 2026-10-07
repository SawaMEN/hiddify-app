package com.hiddify.hiddify.nativerouting

import org.junit.Assert.*
import org.junit.Test

class NativePerAppFlagsTest {
    @Test fun togglesManualSelectionInOneClick() {
        assertEquals(1, NativePerAppFlags.toggle(0))
        assertEquals(0, NativePerAppFlags.toggle(1))
    }
    @Test fun togglesAutomaticSelectionVisiblyInOneClick() {
        assertTrue(NativePerAppFlags.selected(4))
        assertEquals(6, NativePerAppFlags.toggle(4))
        assertFalse(NativePerAppFlags.selected(6))
        assertEquals(5, NativePerAppFlags.toggle(6))
        assertTrue(NativePerAppFlags.selected(5))
        assertEquals(6, NativePerAppFlags.toggle(5))
    }
    @Test fun importedForcedDeselectionCanBeSelectedImmediately() {
        assertFalse(NativePerAppFlags.selected(2))
        assertEquals(1, NativePerAppFlags.toggle(2))
    }
    @Test fun restoresManualFlagsWithoutLosingAutomaticPolicy() {
        assertEquals(5, NativePerAppFlags.restoreManual(6, true))
        assertEquals(6, NativePerAppFlags.restoreManual(5, false))
        assertEquals(1, NativePerAppFlags.restoreManual(2, true))
        assertEquals(2, NativePerAppFlags.restoreManual(1, false))
    }
    @Test fun ignoresUnknownFlagsForEffectiveSelection() {
        assertFalse(NativePerAppFlags.selected(8))
        assertEquals(9, NativePerAppFlags.toggle(8))
        assertEquals(8, NativePerAppFlags.toggle(9))
        assertEquals(13, NativePerAppFlags.restoreManual(14, true))
    }
}
