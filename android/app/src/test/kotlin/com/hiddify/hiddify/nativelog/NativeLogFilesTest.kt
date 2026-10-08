package com.hiddify.hiddify.nativelog

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class NativeLogFilesTest {
    @Test fun tailDropsIncompleteUtf8LineButPreservesExactLineBoundary() {
        val file = File.createTempFile("native-log-", ".txt")
        try {
            file.writeText("старое сообщение\nlatest\n")
            assertEquals("latest\n", NativeLogFiles.readTail(file, 7))
            assertEquals("latest\n", NativeLogFiles.readTail(file, 10))
            assertEquals("", NativeLogFiles.readTail(file, 2))
            file.writeText("строка без переноса")
            assertEquals("", NativeLogFiles.readTail(file, 5))
        } finally { file.delete() }
    }
    @Test fun clearReportsFailureAndStillClearsOtherSources() {
        val directory = Files.createTempDirectory("native-log-").toFile()
        val file = File(directory, "app.log").apply { writeText("message") }
        val bad = File(directory, "box.log").apply { mkdir() }
        try {
            assertTrue(runCatching { NativeLogFiles.clear(listOf(bad, file)) }.isFailure)
            assertEquals("", file.readText())
        } finally { directory.deleteRecursively() }
    }
}
