package com.hiddify.hiddify.nativelog

import org.junit.Assert.*
import org.junit.Test

class NativeProcessExitReasonTest {
    @Test fun distinguishesCrashMemoryAndSystemTermination() {
        assertEquals("low_memory", NativeProcessExitReason.name(3))
        assertEquals("jvm_crash", NativeProcessExitReason.name(4))
        assertEquals("native_crash", NativeProcessExitReason.name(5))
        assertEquals("anr", NativeProcessExitReason.name(6))
        assertEquals("user_requested", NativeProcessExitReason.name(10))
        assertEquals("package_updated", NativeProcessExitReason.name(16))
        assertEquals("memory_limiter", NativeProcessExitReason.name(17))
        assertEquals("resource_anomaly", NativeProcessExitReason.name(18))
        assertEquals("unknown_100", NativeProcessExitReason.name(100))
    }

    @Test fun unknownSignalIsNotReportedAsMemoryOrNativeCrash() {
        val line = NativeProcessExitReason.format(0, 2, 0, 123, "app.vetroff.client", 100, "connected", null)
        assertTrue(line.startsWith("1970-01-01T00:00:00Z ERROR"))
        assertTrue(line.contains("reason=signal status=0 signal=unreported"))
        assertFalse(line.contains("low_memory"))
        assertFalse(line.contains("native_crash"))
        assertEquals("SIGKILL", NativeProcessExitReason.signal(9))
        assertEquals("SIGSEGV", NativeProcessExitReason.signal(11))
    }

    @Test fun expectedExitsDoNotAppearInTheErrorFilterAndDescriptionsCannotForgeLogLines() {
        val line = NativeProcessExitReason.format(0, 16, 0, 123, "app", 100, "stopped", "updated\nERROR fake")
        assertEquals("INFO", NativeLogPresentation.level(line))
        assertFalse(line.contains('\n'))
        assertTrue(NativeLogPresentation.visible(listOf(line), "", "ERROR").isEmpty())
        val crash = NativeProcessExitReason.format(0, 5, 11, 123, "app", 100, "connected", "native crash")
        assertEquals(listOf(crash), NativeLogPresentation.visible(listOf(crash), "", "ERROR"))
    }
}
