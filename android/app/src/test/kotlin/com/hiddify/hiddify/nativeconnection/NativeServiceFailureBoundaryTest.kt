package com.hiddify.hiddify.nativeconnection

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NativeServiceFailureBoundaryTest {
    @Test fun failingSupervisedLaunchReachesCleanupWithoutTerminatingOtherTasks() = runBlocking {
        val received = CompletableDeferred<Exception>()
        val boundary = NativeServiceFailureBoundary({ received.complete(it) }, { throw AssertionError(it) })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + boundary)
        try {
            val failure = IllegalStateException("teardown failure")
            val sibling = scope.launch { awaitCancellation() }
            scope.launch { throw failure }.join()
            assertSame(failure, withTimeout(3000) { received.await() })
            assertTrue(sibling.isActive)
        } finally { scope.cancel() }
    }

    @Test fun concurrentFailuresScheduleOnlyOneCleanup() = runBlocking {
        val received = CopyOnWriteArrayList<Exception>()
        val boundary = NativeServiceFailureBoundary({ received += it }, { throw AssertionError(it) })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + boundary)
        val ready = CompletableDeferred<Unit>()
        try {
            val jobs = List(32) { scope.launch { ready.await(); throw java.io.IOException("callback failed") } }
            ready.complete(Unit)
            jobs.joinAll()
            assertEquals(1, received.size)
        } finally { scope.cancel() }
    }

    @Test fun normalCancellationDoesNotStopTheConnection() = runBlocking {
        val calls = AtomicInteger()
        val boundary = NativeServiceFailureBoundary({ calls.incrementAndGet() }, { calls.incrementAndGet() })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + boundary)
        try {
            scope.launch { throw CancellationException("screen closed") }.join()
            boundary.handleException(EmptyCoroutineContext, CancellationException("user stopped"))
            assertEquals(0, calls.get())
        } finally { scope.cancel() }
    }

    @Test fun fatalVmErrorsStillReachAndroidEvenAfterARecoverableFailure() {
        val fatal = mutableListOf<Throwable>()
        val boundary = NativeServiceFailureBoundary({}, { fatal += it })
        boundary.handleException(EmptyCoroutineContext, IllegalArgumentException())
        val oom = OutOfMemoryError("heap exhausted")
        val linkage = UnsatisfiedLinkError("missing core")
        boundary.handleException(EmptyCoroutineContext, oom)
        boundary.handleException(EmptyCoroutineContext, linkage)
        assertEquals(listOf(oom, linkage), fatal)
    }

    @Test fun aNewStartOnABoundServiceCanReportItsOwnFailure() {
        var calls = 0
        val boundary = NativeServiceFailureBoundary({ calls++ }, { throw AssertionError(it) })
        boundary.handleException(EmptyCoroutineContext, java.io.IOException())
        boundary.handleException(EmptyCoroutineContext, java.io.IOException())
        assertEquals(1, calls)
        boundary.reset()
        boundary.handleException(EmptyCoroutineContext, java.io.IOException())
        assertEquals(2, calls)
    }
}
