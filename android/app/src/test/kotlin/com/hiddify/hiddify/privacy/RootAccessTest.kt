package com.hiddify.hiddify.privacy

import org.junit.Test
import java.io.File
import java.nio.file.Files

class RootAccessTest {
    @Test fun discoveryDoesNotExecuteCommands() {
        val directory = Files.createTempDirectory("root-access-test").toFile()
        try {
            val marker = File(directory, "executed")
            val su = File(directory, "su")
            su.writeText("#!/bin/sh\ntouch '${marker.absolutePath}'\n")
            su.setExecutable(true)
            check(RootAccess.findExecutable(directory.absolutePath) == su.absolutePath)
            check(!marker.exists()) { "Discovery must not execute su or request a permission" }
            su.setExecutable(false, false)
            check(RootAccess.findExecutable(directory.absolutePath) != su.absolutePath)
            su.delete()
            su.mkdir()
            check(RootAccess.findExecutable(directory.absolutePath) != su.absolutePath)
        } finally { directory.deleteRecursively() }
}
}
