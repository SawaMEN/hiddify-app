package com.hiddify.hiddify.nativelog

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class NativeAppLogExportTest {
    @Test fun exportsCurrentSystemEvidenceAlongsideOlderJvmCrash() {
        val directory = Files.createTempDirectory("app-log-export").toFile()
        try {
            val crash = directory.resolve("app.log").apply { writeText("old JVM crash") }
            val exits = directory.resolve("last-exits.json").apply { writeText("[{\"reasonName\":\"native_crash\"}]") }
            val destination = directory.resolve("export.log")
            NativeLogFiles.exportAppSnapshot(listOf(crash, crash, exits, directory.resolve("missing")), destination, 4096)
            val report = destination.readText()
            assertTrue(report.contains("old JVM crash"))
            assertTrue(report.contains("native_crash"))
            assertEquals(1, Regex("=== app.log ===").findAll(report).count())
            assertFalse(report.contains(directory.absolutePath))
        } finally { directory.deleteRecursively() }
    }

    @Test fun boundsEachSourceInExport() {
        val directory = Files.createTempDirectory("app-log-export").toFile()
        try {
            val crash = directory.resolve("app.log").apply { writeText("ignored\n".repeat(10_000) + "last crash\n") }
            val destination = directory.resolve("export.log")
            NativeLogFiles.exportAppSnapshot(listOf(crash), destination, 256)
            assertTrue(destination.readText().contains("last crash"))
            assertTrue(destination.length() < 300)
        } finally { directory.deleteRecursively() }
    }
}
