package com.hiddify.hiddify.nativecore

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class NativeStatsStreamTest {
    @Test fun noSubscribersMeansNoRpc() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val calls = AtomicInteger()
            val stream = NativeStatsStream(scope, { calls.incrementAndGet(); NativeSystemStats() }, { true }, { 1000 })
            delay(40)
            assertEquals(0, calls.get())
            assertFalse(stream.snapshots.value.trafficAvailable)
        } finally { scope.cancel() }
    }
    @Test fun notificationAndScreenShareOneRequest() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val calls = AtomicInteger()
            val requested = CompletableDeferred<Unit>()
            val response = CompletableDeferred<NativeSystemStats>()
            val stream = NativeStatsStream(scope, { calls.incrementAndGet(); requested.complete(Unit); response.await() }, { true }, { 1000 })
            val first = async { stream.snapshots.first { it.trafficAvailable } }
            val second = async { stream.snapshots.first { it.trafficAvailable } }
            withTimeout(2000) { requested.await() }
            response.complete(NativeSystemStats(trafficAvailable = true, downlinkTotal = 1234))
            val both = withTimeout(2000) { listOf(first.await(), second.await()) }
            assertEquals(both[0], both[1])
            assertEquals(1, calls.get())
        } finally { scope.cancel() }
    }
    @Test fun unsubscribeCancelsOutstandingRead() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val requested = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val stream = NativeStatsStream(scope, {
                requested.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }, { true }, { 1000 })
            val observer = launch { stream.snapshots.collect {} }
            withTimeout(2000) { requested.await() }
            observer.cancelAndJoin()
            withTimeout(2000) { cancelled.await() }
        } finally { scope.cancel() }
    }
    @Test fun transientGrpcFailureRetainsCountersAndResetsRateBaseline() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val collectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val updates = Channel<NativeSystemStats>(Channel.UNLIMITED)
        val recovered = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val time = AtomicLong(0)
        try {
            val stream = NativeStatsStream(scope, load = {
                when (calls.incrementAndGet()) {
                    1 -> NativeSystemStats(trafficAvailable = true, currentProfile = "active", downlinkTotal = 500)
                    2 -> NativeSystemStats(trafficAvailable = true, currentProfile = "active", downlinkTotal = 600)
                    3 -> throw IllegalStateException("gRPC transport failure")
                    else -> {
                        recovered.await()
                        NativeSystemStats(trafficAvailable = true, currentProfile = "active", downlinkTotal = 700)
                    }
                }
            }, connected = { true }, clock = { time.addAndGet(1000) }, intervalMillis = 30)
            val collector = collectorScope.launch { stream.snapshots.collect { updates.send(it) } }
            suspend fun until(predicate: (NativeSystemStats) -> Boolean): NativeSystemStats =
                withTimeout(4000) {
                    while (true) {
                        val snapshot = updates.receive()
                        if (predicate(snapshot)) return@withTimeout snapshot
                    }
                    @Suppress("UNREACHABLE_CODE")
                    NativeSystemStats()
                }
            val normal = until { it.speedAvailable && it.downlinkTotal == 600L }
            assertTrue(normal.downlink > 0)
            val interrupted = until { it.downlinkTotal == 600L && !it.speedAvailable }
            assertEquals("active", interrupted.currentProfile)
            assertEquals(0L, interrupted.downlink)
            recovered.complete(Unit)
            val firstRecovered = until { it.downlinkTotal == 700L }
            assertFalse(firstRecovered.speedAvailable)
            assertEquals(0L, firstRecovered.downlink)
            collector.cancelAndJoin()
        } finally {
            recovered.complete(Unit)
            collectorScope.cancel()
            scope.cancel()
            updates.close()
        }
    }

    @Test fun resetRejectsLateResponseFromPreviousSession() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val collectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val count = AtomicInteger()
            val oldRequested = CompletableDeferred<Unit>()
            val releaseOld = CompletableDeferred<Unit>()
            val stream = NativeStatsStream(scope, {
                if (count.incrementAndGet() == 1) {
                    oldRequested.complete(Unit)
                    withContext(NonCancellable) { releaseOld.await() }
                    NativeSystemStats(trafficAvailable = true, currentProfile = "old")
                } else NativeSystemStats(trafficAvailable = true, currentProfile = "new")
            }, { true }, { 1000 })
            val profiles = java.util.Collections.synchronizedList(mutableListOf<String>())
            val observer = collectorScope.launch { stream.snapshots.collect { profiles.add(it.currentProfile) } }
            withTimeout(2000) { oldRequested.await() }
            stream.reset()
            releaseOld.complete(Unit)
            withTimeout(2000) { stream.snapshots.first { it.currentProfile == "new" } }
            observer.cancelAndJoin()
            assertFalse(profiles.contains("old"))
        } finally { collectorScope.cancel(); scope.cancel() }
    }
}
