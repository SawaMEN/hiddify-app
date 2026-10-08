package com.hiddify.hiddify.nativecore

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class NativeRuntimeInitializationTest {
    @Test fun constructionDoesNotLoadTheCore() {
        var calls = 0
        val runtime = NativeRuntimeInitialization { calls++ }
        assertEquals(0, calls)
        runtime.ensureLoaded()
        runtime.ensureLoaded()
        assertEquals(1, calls)
    }

    @Test fun missingNativeLibraryBecomesAnOrdinaryRecoverableError() {
        var calls = 0
        val cause = UnsatisfiedLinkError("missing libgojni.so")
        val runtime = NativeRuntimeInitialization { calls++; throw cause }
        repeat(2) {
            try {
                runtime.ensureLoaded()
                fail("Expected a recoverable error")
            } catch (error: IllegalStateException) {
                assertSame(cause, error.cause)
            }
        }
        assertEquals(1, calls)
    }

    @Test fun failedGeneratedBindingInitializationIsAlsoRecoverable() {
        val cause = ExceptionInInitializerError(IllegalStateException("JNI binding initialization failed"))
        val runtime = NativeRuntimeInitialization { throw cause }
        try {
            runtime.ensureLoaded()
            fail("Expected a recoverable error")
        } catch (error: IllegalStateException) {
            assertSame(cause, error.cause)
        }
    }

    @Test fun simultaneousValidationAndServiceStartupLoadOnlyOnce() {
        val gate = CountDownLatch(1)
        var calls = 0
        val runtime = NativeRuntimeInitialization { calls++ }
        val workers = List(8) { thread { gate.await(); runtime.ensureLoaded() } }
        gate.countDown()
        workers.forEach { it.join() }
        assertEquals(1, calls)
    }
}
