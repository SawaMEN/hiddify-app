package com.hiddify.hiddify.privacy

import com.hiddify.hiddify.nativerouting.NativeInstalledApp
import org.junit.Assert.*
import org.junit.Test

class NativeRegionalApplicationsTest {
    @Test fun unavailableSavedAppsDoNotAppearWithoutInstalledApps() {
        assertTrue(NativeRegionalApplications.rows(emptyList(), setOf("com.example.removed")).isEmpty())
    }

    @Test fun listsEveryInstalledAppIncludingUnselectedAppsOutsideTheCatalogue() {
        val installed = listOf(NativeInstalledApp("com.example.chat", "My chat", true),
            NativeInstalledApp("com.example.browser", "Browser", false))
        val rows = NativeRegionalApplications.rows(installed + installed.first(), setOf("com.example.removed", "com.example.chat"))
        assertEquals(2, rows.size)
        assertEquals(rows.size, rows.map { it.packageName }.distinct().size)
        assertEquals(installed.first(), rows.first { it.packageName == "com.example.chat" })
        assertEquals("com.example.chat", rows.first().packageName)
        assertTrue(rows.any { it.packageName == "com.example.browser" })
        assertFalse(rows.any { it.packageName == "com.example.removed" })
    }

    @Test fun directRoutingCanStillDisplayItsSavedMissingChoices() {
        val rows = NativeRegionalApplications.rows(emptyList(), setOf("com.example.removed"), includeMissing = true)
        assertEquals(listOf("com.example.removed"), rows.map { it.packageName })
    }
}
