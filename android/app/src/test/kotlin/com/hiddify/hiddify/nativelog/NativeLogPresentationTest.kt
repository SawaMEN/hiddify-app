package com.hiddify.hiddify.nativelog

import org.junit.Assert.*
import org.junit.Test

class NativeLogPresentationTest {
    @Test fun terminalColorsAreRemovedWithoutChangingMessages() {
        assertEquals("INFO connection established", NativeLogPresentation.clean("\u001B[32mINFO\u001B[0m connection established\r"))
    }
    @Test fun severityIsAMinimumAndUnknownEntriesRemainVisible() {
        val lines = listOf("INFO connected", "ERROR connection failed", "WARN disconnected", "unstructured line")
        assertEquals(listOf("ERROR connection failed", "WARN disconnected", "unstructured line"),
            NativeLogPresentation.visible(lines, "", "WARN"))
        assertEquals(listOf("ERROR connection failed"), NativeLogPresentation.visible(lines, "connection", "INFO"))
        assertTrue(NativeLogPresentation.visible(lines, "CONNECTION", null).isEmpty())
        assertTrue(NativeLogPresentation.visible(lines, " connection ", null).isEmpty())
    }
    @Test fun severityWordsInsideTheMessageAreNotAHeader() {
        assertNull(NativeLogPresentation.level("request body contains ERROR but completed successfully"))
        assertEquals("WARN", NativeLogPresentation.level("[box.log] WARNING network unavailable"))
    }
    @Test fun timestampAndSourceAreSeparatedFromTheMessage() {
        val entry = NativeLogPresentation.entry("[box.log] +0300 2026-10-08 12:30:05 INFO connected")
        assertEquals("INFO", entry.level)
        assertEquals("2026-10-08 12:30:05", entry.time)
        assertEquals("connected", entry.message)
        assertEquals(listOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "PANIC"), NativeLogPresentation.levels)
    }
    @Test fun errorFilterIncludesFatalFailuresAndExcludesLowerSeverities() {
        val lines = listOf("INFO connected", "WARN retry", "ERROR disconnected", "FATAL crashed", "PANIC stopped")
        assertTrue("ERROR" in NativeLogPresentation.levels)
        assertEquals(lines.drop(2), NativeLogPresentation.visible(lines, "", "ERROR"))
        assertEquals(lines.takeLast(1), NativeLogPresentation.visible(lines, "", "PANIC"))
    }
    @Test fun westernTimezoneOffsetsKeepTimestampAndSeverity() {
        val entry = NativeLogPresentation.entry("[box.log] -0500 2026-10-08 12:00:00 WARN retry")
        assertEquals("WARN", entry.level)
        assertEquals("2026-10-08 12:00:00", entry.time)
        assertEquals("retry", entry.message)
    }
}
