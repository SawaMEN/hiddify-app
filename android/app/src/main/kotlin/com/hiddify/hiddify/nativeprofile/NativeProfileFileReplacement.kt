package com.hiddify.hiddify.nativeprofile

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Replace configuration before committing metadata, retaining recovery data on rollback failure. */
internal object NativeProfileFileReplacement {
    fun <T> replace(destination: File, content: String, commit: () -> T): T {
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size <= NativeProfileTransfer.MAX_CONFIG_BYTES) { "Configuration exceeds 8 MiB" }
        require(!destination.exists() || destination.isFile) { "Configuration path is not a file" }
        val directory = requireNotNull(destination.absoluteFile.parentFile)
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create configuration directory" }
        val token = UUID.randomUUID()
        val temp = File(directory, "${destination.name}.tmp-$token")
        val backup = File(directory, "${destination.name}.backup-$token")
        val hadDestination = destination.exists()
        var replaced = false
        var committed = false
        try {
            temp.outputStream().use { output -> output.write(bytes); output.fd.sync() }
            if (hadDestination) destination.copyTo(backup)
            moveReplacing(temp, destination)
            replaced = true
            val result = commit()
            committed = true
            return result
        } catch (error: Throwable) {
            if (replaced && !committed) {
                try {
                    if (hadDestination) moveReplacing(backup, destination)
                    else check(destination.delete()) { "Cannot roll back new configuration" }
                } catch (restoreError: Throwable) { error.addSuppressed(restoreError) }
            }
            throw error
        } finally {
            temp.delete()
            if (committed || !replaced) backup.delete()
        }
    }

    private fun moveReplacing(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
