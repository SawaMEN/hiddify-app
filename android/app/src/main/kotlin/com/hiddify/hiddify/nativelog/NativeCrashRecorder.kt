package com.hiddify.hiddify.nativelog

import java.io.File
import java.time.Instant

/** Preserve the last JVM crash across restarts, then let Android terminate normally. */
internal class NativeCrashRecorder(
    private val file: File,
    private val previous: Thread.UncaughtExceptionHandler,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        try { record(file, error, "JVM crash") }
        finally { previous.uncaughtException(thread, error) }
    }

    companion object {
        private val reportLock = Any()

        fun recordServiceFailure(file: File, error: Exception) = record(file, error, "Handled VPN service failure")

        private fun record(file: File, error: Throwable, title: String) {
            try {
                // Exception messages and thread names may contain URLs, credentials or profile data.
                // Retain only types and bounded stack frames, including the underlying cause.
                val report = buildString {
                    append(Instant.now()).append(" ERROR ").append(title).append('\n')
                    var cause: Throwable? = error
                    val seen = java.util.IdentityHashMap<Throwable, Boolean>()
                    repeat(8) {
                        val current = cause ?: return@repeat
                        if (seen.put(current, true) != null) { cause = null; return@repeat }
                        append(current.javaClass.name).append('\n')
                        current.stackTrace.take(64).forEach { append("  at ").append(it).append('\n') }
                        cause = current.cause
                    }
                }.take(64 * 1024)
                synchronized(reportLock) { file.writeText(report) }
            } catch (_: Throwable) {
                // Disk failures or OOM must never prevent the original crash handler from running.
            }
        }
    }
}
