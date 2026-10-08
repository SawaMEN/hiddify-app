package com.hiddify.hiddify.nativecore

import java.util.concurrent.atomic.AtomicInteger
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
