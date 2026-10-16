package com.hiddify.hiddify.privacy

import com.hiddify.hiddify.nativerouting.NativeInstalledApp
import org.junit.Assert.*
import org.junit.Test

class NativeRegionalApplicationsTest {
    private val services = listOf(
        NativeRegionalService("chat", "Chat", listOf("com.example.chat", "com.example.chat.lite"), emptyList()),
        NativeRegionalService("video", "Video", listOf("com.example.video", "com.example.chat"), emptyList()),
    )

    @Test fun catalogueRemainsSelectableWithoutInstalledApps() {
        val rows = NativeRegionalApplications.rows(emptyList(), services, emptySet())
        assertEquals(setOf("com.example.chat", "com.example.chat.lite", "com.example.video"),
            rows.map { it.packageName }.toSet())
        assertEquals(3, rows.size)
        assertEquals("Chat", rows.first { it.packageName == "com.example.chat" }.label)
    }

    @Test fun keepsInstalledLabelsAndCustomSavedChoicesWithoutDuplicatePackages() {
        val installed = listOf(NativeInstalledApp("com.example.chat", "My chat", true),
            NativeInstalledApp("com.example.browser", "Browser", false))
        val rows = NativeRegionalApplications.rows(installed, services, setOf("com.example.removed", "com.example.chat"))
        assertEquals(5, rows.size)
        assertEquals(rows.size, rows.map { it.packageName }.distinct().size)
        assertEquals(installed.first(), rows.first { it.packageName == "com.example.chat" })
        assertEquals(setOf("com.example.chat", "com.example.removed"), rows.take(2).map { it.packageName }.toSet())
        assertTrue(rows.any { it.packageName == "com.example.browser" })
    }
}
