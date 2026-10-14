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
}
