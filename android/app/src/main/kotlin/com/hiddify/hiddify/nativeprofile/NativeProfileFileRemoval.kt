package com.hiddify.hiddify.nativeprofile

import java.io.File
import java.util.UUID

/** Stage removal before committing the database, restoring the file if the transaction fails. */
internal object NativeProfileFileRemoval {
    fun <T> remove(file: File, commit: () -> T): T {
        val staged = File(file.parentFile, "${file.name}.delete-${UUID.randomUUID()}")
        val hadFile = file.exists()
        if (hadFile) check(file.isFile && file.renameTo(staged)) { "Unable to remove profile configuration" }
        val result = try {
            commit()
        } catch (error: Throwable) {
            if (hadFile && !staged.renameTo(file)) {
                error.addSuppressed(IllegalStateException("Unable to restore profile configuration: ${staged.name}"))
            }
            throw error
        }
        // A committed deletion must not be reported as failed just because backup cleanup failed.
        if (hadFile) staged.delete()
        return result
    }
}
