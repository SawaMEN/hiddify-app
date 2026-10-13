package com.hiddify.hiddify.nativelog

import java.io.File
import java.time.Instant

/** Preserve the last JVM crash across restarts, then let Android terminate normally. */
internal class NativeCrashRecorder(
    private val file: File,
    private val previous: Thread.UncaughtExceptionHandler,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            // Exception messages and thread names may contain URLs, credentials or profile data.
            // Retain only types and bounded stack frames, including the underlying cause.
            val report = buildString {
                append("JVM crash at ").append(Instant.now()).append('\n')
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
            synchronized(this) { file.writeText(report) }
        } catch (_: Throwable) {
            // Disk failures or OOM must never prevent the original crash handler from running.
        } finally {
            previous.uncaughtException(thread, error)
        }
    }
}
