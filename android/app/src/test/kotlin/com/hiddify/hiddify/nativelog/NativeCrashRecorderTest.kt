package com.hiddify.hiddify.nativelog

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class NativeCrashRecorderTest {
    @Test fun recordsHandledServiceFailureSeparatelyWithoutSecretsOrTermination() {
        val directory = Files.createTempDirectory("service-failure-recorder").toFile()
        try {
            val crash = File(directory, "app.log").apply { writeText("previous fatal crash") }
            val serviceFailure = File(directory, "service-failure.log")
            NativeCrashRecorder.recordServiceFailure(serviceFailure,
                IllegalStateException("private-profile", SecurityException("private-token")))
            val report = serviceFailure.readText()
            assertEquals("ERROR", NativeLogPresentation.entry(report.lineSequence().first()).level)
            assertTrue(report.contains("Handled VPN service failure"))
            assertTrue(report.contains("java.lang.SecurityException"))
            assertFalse(report.contains("private-profile"))
            assertFalse(report.contains("private-token"))
            assertEquals("previous fatal crash", crash.readText())
            // A failed diagnostic write must not interrupt cleanup.
            NativeCrashRecorder.recordServiceFailure(directory, IllegalArgumentException())
        } finally { directory.deleteRecursively() }
    }

    @Test fun recordsCauseWithoutSecretsAndDelegatesOriginalCrash() {
        val directory = Files.createTempDirectory("crash-recorder").toFile()
        try {
            val file = File(directory, "app.log")
            val error = IllegalStateException("secret-profile", SecurityException("secret-token"))
            val thread = Thread.currentThread()
            var calls = 0
            val recorder = NativeCrashRecorder(file, Thread.UncaughtExceptionHandler { actualThread, actualError ->
                assertSame(thread, actualThread)
                assertSame(error, actualError)
                calls++
            })
            recorder.uncaughtException(thread, error)
            val report = file.readText()
            assertTrue(report.contains("java.lang.IllegalStateException"))
            assertTrue(report.contains("java.lang.SecurityException"))
            assertTrue(report.contains("NativeCrashRecorderTest"))
            assertEquals("ERROR", NativeLogPresentation.entry(report.lineSequence().first()).level)
            assertFalse(report.contains("secret-profile"))
            assertFalse(report.contains("secret-token"))
            assertEquals(1, calls)
        } finally { directory.deleteRecursively() }
    }

    @Test fun delegatesEvenWhenCrashFileCannotBeWritten() {
        val directory = Files.createTempDirectory("crash-recorder").toFile()
        try {
            var calls = 0
            NativeCrashRecorder(directory, Thread.UncaughtExceptionHandler { _, _ -> calls++ })
                .uncaughtException(Thread.currentThread(), IllegalStateException())
            assertEquals(1, calls)
        } finally { directory.deleteRecursively() }
    }

    @Test fun replacesPreviousCrashAndBoundsRecursiveCauses() {
        val directory = Files.createTempDirectory("crash-recorder").toFile()
        try {
            val file = File(directory, "app.log").apply { writeText("old crash") }
            val first = IllegalStateException()
            val second = IllegalArgumentException(first)
            first.initCause(second)
            NativeCrashRecorder(file, Thread.UncaughtExceptionHandler { _, _ -> })
                .uncaughtException(Thread.currentThread(), first)
            val report = file.readText()
            assertFalse(report.contains("old crash"))
            assertEquals(1, Regex("java.lang.IllegalStateException").findAll(report).count())
            assertEquals(1, Regex("java.lang.IllegalArgumentException").findAll(report).count())
            assertTrue(report.length <= 64 * 1024)
        } finally { directory.deleteRecursively() }
    }
}
