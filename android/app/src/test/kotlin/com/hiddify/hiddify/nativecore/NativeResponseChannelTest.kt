package com.hiddify.hiddify.nativecore

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class NativeResponseChannelTest {
    @Test fun firstResponseClosesRequestsAndDiscardsUnusedResponses() = runBlocking {
        val requests = Channel<String>(1)
        val responses = Channel<Int>(Channel.UNLIMITED)
        val cancelled = AtomicInteger()
        responses.send(1); responses.send(2)
        assertEquals(1, withNativeResponseChannel("request", { requests to responses }, { cancelled.incrementAndGet() }) {
            assertEquals("request", requests.receive())
            assertTrue(requests.receiveCatching().isClosed)
            it.nativeFirstOrNull()
        })
        assertTrue(responses.receiveCatching().isClosed)
        assertEquals(1, cancelled.get())
    }

    @Test fun normalEofReturnsNullAndTransportFailureIsNotHidden() = runBlocking {
        for (failure in listOf(null, IOException("transport failed"))) {
            val requests = Channel<String>(1)
            val responses = Channel<Int>().apply { close(failure) }
            var cancelled = false
            try {
                val result = withNativeResponseChannel("request", { requests to responses }, { cancelled = true }) { it.nativeFirstOrNull() }
                assertNull(result)
                assertNull(failure)
            } catch (error: IOException) { assertEquals("transport failed", error.message) }
            assertTrue(cancelled)
        }
    }

    @Test fun cancellationWhileWaitingStopsTheTransportAndJoinsScopedCleanup() = runBlocking {
        val requests = Channel<String>(1)
        val responses = Channel<Int>()
        val entered = CompletableDeferred<Unit>()
        val transportStopped = CompletableDeferred<Unit>()
        val cleaned = CompletableDeferred<Unit>()
        val cancelled = AtomicInteger()
        val job = launch {
            withNativeResponseChannel("request", { scope ->
                scope.launch {
                    try { awaitCancellation() }
                    finally { withContext(NonCancellable) { transportStopped.await(); cleaned.complete(Unit) } }
                }
                requests to responses
            }, { cancelled.incrementAndGet(); transportStopped.complete(Unit) }) {
                entered.complete(Unit)
                it.nativeFirstOrNull()
            }
        }
        withTimeout(5_000) { entered.await(); job.cancelAndJoin(); cleaned.await() }
        assertEquals(1, cancelled.get())
        assertTrue(responses.receiveCatching().isClosed)
    }

    @Test fun cancellationWhileSendingAlsoClosesBothDirections() = runBlocking {
        val requests = Channel<String>() // No reader: sending the request suspends.
        val responses = Channel<Int>()
        val opened = CompletableDeferred<Unit>()
        var cancelled = false
        val job = launch {
            withNativeResponseChannel("request", { opened.complete(Unit); requests to responses }, { cancelled = true }) { it.nativeFirstOrNull() }
        }
        withTimeout(5_000) { opened.await(); job.cancelAndJoin() }
        assertTrue(cancelled)
        assertTrue(requests.receiveCatching().isClosed)
        assertTrue(responses.receiveCatching().isClosed)
    }

    @Test fun openingFailureStillCancelsTheTransport() = runBlocking {
        var cancelled = false
        try {
            withNativeResponseChannel<String, Int, Int?>("request", { throw IOException("open failed") }, { cancelled = true }) { it.nativeFirstOrNull() }
            fail("Expected transport failure")
        } catch (error: IOException) { assertEquals("open failed", error.message) }
        assertTrue(cancelled)
    }

    @Test fun flowShortCircuitClosesTransportWithoutWaitingForStreamEof() = runBlocking {
        val requests = Channel<String>(1)
        val responses = Channel<Int>(Channel.UNLIMITED).apply { trySend(1); trySend(2) }
        var cancelled = false
        val stream = flow {
            withNativeResponseChannel("request", { requests to responses }, { cancelled = true }) { channel ->
                for (value in channel) emit(value)
            }
        }
        assertEquals(1, withTimeout(5_000) { stream.first() })
        assertTrue(cancelled)
        assertTrue(responses.receiveCatching().isClosed)
    }
}
