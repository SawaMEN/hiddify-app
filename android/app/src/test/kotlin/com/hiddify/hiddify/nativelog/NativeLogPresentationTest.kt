package com.hiddify.hiddify.nativelog

import org.junit.Assert.*
import org.junit.Test

class NativeLogPresentationTest {
    @Test fun terminalColorsAreRemovedWithoutChangingMessages() {
        assertEquals("INFO connection established", NativeLogPresentation.clean("\u001B[32mINFO\u001B[0m connection established\r"))
    }
    @Test fun levelAndSearchFiltersAreIndependent() {
        val lines = listOf("INFO connected", "ERROR connection failed", "WARN disconnected", "unstructured line")
        assertEquals(listOf("ERROR connection failed"), NativeLogPresentation.visible(lines, "CONNECTION", "ERROR"))
        assertEquals(listOf("unstructured line"), NativeLogPresentation.visible(lines, "unstructured", null))
        assertTrue(NativeLogPresentation.visible(lines, "unstructured", "INFO").isEmpty())
    }
    @Test fun warningsAreNormalizedAndUnknownLinesRemainUnknown() {
        assertEquals("WARN", NativeLogPresentation.level("[box.log] WARNING network unavailable"))
        assertNull(NativeLogPresentation.level("connection failed without a structured severity"))
        assertNull(NativeLogPresentation.level("information received"))
    }
}
