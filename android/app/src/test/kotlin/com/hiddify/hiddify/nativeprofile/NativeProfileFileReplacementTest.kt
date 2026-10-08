package com.hiddify.hiddify.nativeprofile

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class NativeProfileFileReplacementTest {
    private fun temporary(action: (java.io.File) -> Unit) {
        val directory = Files.createTempDirectory("profile-replace-test").toFile()
        try { action(directory) } finally { directory.deleteRecursively() }
    }

    @Test fun metadataCommitSeesNewContentAndSuccessfulReplacementHasNoBackups() = temporary { directory ->
        val file = directory.resolve("profile.json").apply { writeText("old") }
        val result = NativeProfileFileReplacement.replace(file, "new") { assertEquals("new", file.readText()); 42 }
        assertEquals(42, result)
        assertEquals("new", file.readText())
        assertEquals(listOf("profile.json"), directory.list()!!.toList())
    }

    @Test fun databaseFailureRestoresOldFileAndPreservesOriginalError() = temporary { directory ->
        val file = directory.resolve("profile.json").apply { writeText("old") }
        val error = IllegalStateException("database commit failed")
        assertSame(error, runCatching { NativeProfileFileReplacement.replace(file, "new") { throw error } }.exceptionOrNull())
        assertEquals("old", file.readText())
        assertEquals(1, directory.list()!!.size)
    }

    @Test fun failedNewImportRemovesConfiguration() = temporary { directory ->
        val file = directory.resolve("profile.json")
        assertTrue(runCatching { NativeProfileFileReplacement.replace(file, "new") { error("db failure") } }.isFailure)
        assertFalse(file.exists())
        assertTrue(directory.list()!!.isEmpty())
    }

    @Test fun rollbackFailureRetainsRecoveryCopyAndBothErrors() = temporary { directory ->
        val file = directory.resolve("profile.json").apply { writeText("old") }
        val original = IllegalStateException("metadata failure")
        val error = runCatching {
            NativeProfileFileReplacement.replace(file, "new") {
                assertTrue(file.delete()); assertTrue(file.mkdir())
                file.resolve("block-restore").writeText("occupied")
                throw original
            }
        }.exceptionOrNull()!!
        assertSame(original, error)
        assertEquals(1, error.suppressed.size)
        assertEquals("old", directory.listFiles()!!.single { it.name.contains(".backup-") }.readText())
    }

    @Test fun oversizedContentNeverCreatesTemporaryFilesOrCallsCommit() = temporary { directory ->
        val file = directory.resolve("profile.json").apply { writeText("old") }
        var called = false
        assertTrue(runCatching {
            NativeProfileFileReplacement.replace(file, "x".repeat(NativeProfileTransfer.MAX_CONFIG_BYTES + 1)) { called = true }
        }.isFailure)
        assertFalse(called); assertEquals("old", file.readText()); assertEquals(1, directory.list()!!.size)
    }

    @Test fun directoryDestinationIsRejectedBeforeWriting() = temporary { directory ->
        val file = directory.resolve("profile.json").apply { mkdir() }
        var called = false
        assertTrue(runCatching { NativeProfileFileReplacement.replace(file, "new") { called = true } }.isFailure)
        assertFalse(called); assertTrue(file.isDirectory); assertEquals(1, directory.list()!!.size)
    }
}
