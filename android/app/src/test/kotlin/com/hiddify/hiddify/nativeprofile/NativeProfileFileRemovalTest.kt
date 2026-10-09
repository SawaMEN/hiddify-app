package com.hiddify.hiddify.nativeprofile

import java.nio.file.Files
import org.junit.Test

class NativeProfileFileRemovalTest {
    @Test fun deletionIsTransactionalAndRejectsDirectories() {
        val directory = Files.createTempDirectory("profile-delete-regression").toFile()
        try {
            val config = directory.resolve("active.json").apply { writeText("original config") }
            val transactionError = IllegalStateException("database commit failed")
            val failure = runCatching {
                NativeProfileFileRemoval.remove(config) {
                    check(!config.exists())
                    throw transactionError
                }
            }.exceptionOrNull()
            check(failure === transactionError)
            check(config.readText() == "original config")
            check(directory.listFiles()!!.size == 1)
            val result = NativeProfileFileRemoval.remove(config) { check(!config.exists()); "committed" }
            check(result == "committed" && !config.exists())
            check(directory.listFiles()!!.isEmpty())
            check(NativeProfileFileRemoval.remove(config) { "missing config also deletable" }.isNotEmpty())
            val invalid = directory.resolve("folder.json").apply { mkdir() }
            var committed = false
            check(runCatching { NativeProfileFileRemoval.remove(invalid) { committed = true } }.isFailure)
            check(!committed && invalid.isDirectory)
        } finally { directory.deleteRecursively() }
    }
}
