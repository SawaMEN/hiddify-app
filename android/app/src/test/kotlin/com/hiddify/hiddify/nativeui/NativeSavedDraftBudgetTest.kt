package com.hiddify.hiddify.nativeui

import org.junit.Assert.*
import org.junit.Test

class NativeSavedDraftBudgetTest {
    @Test fun ordinaryDraftsAndSelectionsFit() {
        assertTrue(NativeSavedDraftBudget.fits("пример.рф"))
        assertTrue(NativeSavedDraftBudget.fits(listOf("youtube.com", "docker.io")))
        assertTrue(NativeSavedDraftBudget.fits(emptyList()))
    }

    @Test fun largePastedDraftsDoNotEnterSavedState() {
        assertFalse(NativeSavedDraftBudget.fits("a".repeat(256 * 1024)))
        assertTrue(NativeSavedDraftBudget.fits("a".repeat(8184)))
        assertFalse(NativeSavedDraftBudget.fits("a".repeat(8185)))
        // UTF-16 uses two code units per supplementary character.
        assertFalse(NativeSavedDraftBudget.fits("\uD83D\uDE00".repeat(5000)))
    }

    @Test fun aggregateSelectionAndPerItemOverheadAreBounded() {
        assertFalse(NativeSavedDraftBudget.fits(List(10_000) { "site-$it.example" }))
        assertFalse(NativeSavedDraftBudget.fits(List(2000) { "" }))
        assertFalse(NativeSavedDraftBudget.fits(listOf("a".repeat(9000))))
    }
}
