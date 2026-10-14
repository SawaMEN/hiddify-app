package com.hiddify.hiddify.nativeconnection

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NativeStartupCancellationTest {
    @Test fun simultaneousStopAndDeadlineRequestsShareOneNativeTask() = runBlocking {
        val starting = AtomicBoolean(true)
        val calls = AtomicInteger()
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val cancellation = NativeStartupCancellationTask(scope)
        try {
            val stop = {
                calls.incrementAndGet()
                entered.complete(Unit)
                check(release.await(5, TimeUnit.SECONDS)) { "Test stop callback timed out" }
                starting.set(false)
            }
            val requests = List(64) {
                launch(Dispatchers.Default) { cancellation.request({ starting.get() }, stop) }
            }
            requests.joinAll()
            withTimeout(3000) { entered.await() }
            assertEquals(1, calls.get())
            release.countDown()
            withTimeout(3000) { cancellation.join() }
            assertEquals(1, calls.get())
        } finally { release.countDown(); scope.cancel() }
    }

    @Test fun cleanupWaitsForTheBlockingNativeStopToReturn() = runBlocking {
        val starting = AtomicBoolean(true)
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val cancellation = NativeStartupCancellationTask(scope)
        try {
            cancellation.request({ starting.get() }) {
                entered.complete(Unit)
                check(release.await(5, TimeUnit.SECONDS))
            }
            withTimeout(3000) { entered.await() }
            // JNI Start returned, but JNI Stop is still in flight.
            starting.set(false)
            val cleanup = async(start = CoroutineStart.UNDISPATCHED) { cancellation.join() }
            assertFalse(cleanup.isCompleted)
            release.countDown()
            withTimeout(3000) { cleanup.await() }
        } finally { release.countDown(); scope.cancel() }
    }

    @Test fun subsequentStartGetsItsOwnCancellationAndCompletedStartIsIgnored() = runBlocking {
        val starting = AtomicBoolean(false)
        val calls = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val cancellation = NativeStartupCancellationTask(scope)
        try {
            cancellation.request({ starting.get() }) { calls.incrementAndGet() }
            cancellation.join()
            assertEquals(0, calls.get())
            repeat(2) {
                starting.set(true)
                cancellation.request({ starting.get() }) { calls.incrementAndGet(); starting.set(false) }
                withTimeout(3000) { cancellation.join() }
            }
            assertEquals(2, calls.get())
        } finally { scope.cancel() }
    }

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

    @Test fun startupDeadlineOnlyCancelsActiveOwnedConnections() {
        assertTrue(NativeStartupDeadline.shouldAbort(starting = true, stopRequested = false, connectionDesired = true, ownsCore = true))
        assertFalse(NativeStartupDeadline.shouldAbort(starting = false, stopRequested = false, connectionDesired = true, ownsCore = true))
        assertFalse(NativeStartupDeadline.shouldAbort(starting = true, stopRequested = true, connectionDesired = true, ownsCore = true))
        assertFalse(NativeStartupDeadline.shouldAbort(starting = true, stopRequested = false, connectionDesired = false, ownsCore = true))
        assertFalse(NativeStartupDeadline.shouldAbort(starting = true, stopRequested = false, connectionDesired = true, ownsCore = false))
    }

    @Test fun cancellationNeverStopsAnotherCompletedStartup() = runBlocking {
        NativeStartupCancellation.cancelWhileStarting({ false }) { fail("Must not stop completed startup") }
    }
}
