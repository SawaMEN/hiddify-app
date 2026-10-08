package com.hiddify.hiddify.nativeconnection

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NativeStartupCancellationTest {
    @Test fun cancellationRetriesUntilNativeInstallsItsCancelFunction() = runBlocking {
        val starting = AtomicBoolean(true)
        val entered = CompletableDeferred<Unit>()
        val delivered = CompletableDeferred<Unit>()
        var calls = 0
        val cancellation = launch {
            NativeStartupCancellation.cancelWhileStarting({ starting.get() }) {
                calls++
                if (entered.isCompleted) delivered.complete(Unit)
            }
        }
        yield()
        assertEquals(1, calls)
        entered.complete(Unit)
        withTimeout(2000) { delivered.await() }
        starting.set(false)
        withTimeout(2000) { cancellation.join() }
        assertTrue(calls >= 2)
    }

    @Test fun cancellationNeverStopsAnotherCompletedStartup() = runBlocking {
        NativeStartupCancellation.cancelWhileStarting({ false }) { fail("Must not stop completed startup") }
    }
}
