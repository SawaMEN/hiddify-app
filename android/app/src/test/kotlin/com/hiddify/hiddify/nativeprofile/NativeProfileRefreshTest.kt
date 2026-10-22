package com.hiddify.hiddify.nativeprofile

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class NativeProfileRefreshTest {
    @Test fun lifecycleCancellationStopsABlockedHttpCallAndJoinsCleanup() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.start()
        val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
        val call = client.newCall(Request.Builder().url(server.url("/subscription")).build())
        val cleaned = AtomicBoolean()
        try {
            val job = launch {
                runNativeProfileRefresh { token ->
                    token.attachRequest(call::cancel)
                    try {
                        call.execute().use { token.ensureActive() }
                    } catch (error: Exception) {
                        token.ensureActive()
                        throw error
                    } finally { token.detachRequest(); cleaned.set(true) }
                }
            }
            assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) })
            withTimeout(5_000) { job.cancelAndJoin() }
            assertTrue(call.isCanceled())
            assertTrue(cleaned.get())
        } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow(); server.shutdown() }
    }

    @Test fun cancellationDuringCommitWaitsForAtomicWorkBeforeReleasingTheOperationLock() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val committed = AtomicBoolean()
        val lock = Mutex()
        val job = launch {
            lock.withLock {
                runNativeProfileRefresh { token ->
                    token.beginCommit()
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    token.ensureActive() // Cancellation must not interrupt an entered commit.
                    committed.set(true)
                }
            }
        }
        try {
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            job.cancel()
            assertFalse(lock.tryLock())
            assertFalse(job.isCompleted)
            release.countDown()
            withTimeout(5_000) { job.join() }
            assertTrue(committed.get())
            assertTrue(lock.tryLock())
            lock.unlock()
        } finally { release.countDown(); job.cancelAndJoin() }
    }

    @Test fun cancellationHookFailuresAreReportedWithoutDiscardingWorkerCleanup() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val report = CompletableDeferred<Exception>()
        val cleaned = AtomicBoolean()
        val job = launch {
            runNativeProfileRefresh(onCancellationFailure = { report.complete(it) }) { token ->
                token.attachRequest { release.countDown(); error("cancel hook") }
                entered.countDown()
                try { check(release.await(5, TimeUnit.SECONDS)); token.ensureActive() }
                finally { token.detachRequest(); cleaned.set(true) }
            }
        }
        try {
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            withTimeout(5_000) { job.cancelAndJoin() }
            assertEquals("cancel hook", withTimeout(5_000) { report.await() }.message)
            assertTrue(cleaned.get())
        } finally { release.countDown(); job.cancelAndJoin() }
    }

    @Test fun blockingRefreshReturnsValuesAndPreservesOrdinaryFailures() = runBlocking {
        assertEquals(42, runNativeProfileRefresh { 42 })
        try {
            runNativeProfileRefresh<Int> { error("refresh failed") }
            fail("Expected refresh failure")
        } catch (error: IllegalStateException) { assertEquals("refresh failed", error.message) }
    }

    @Test fun batchKeepsSuccessesAndContinuesAfterOrdinaryFailures() = runBlocking {
        val visited = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val updated = refreshNativeProfileBatch(listOf("a", "bad", "b"), { id ->
            visited += id
            if (id == "bad") error("bad subscription")
        }) { id, error -> failures += "$id:${error.message}" }
        assertEquals(2, updated)
        assertEquals(listOf("a", "bad", "b"), visited)
        assertEquals(listOf("bad:bad subscription"), failures)
    }

    @Test fun batchDoesNotTreatCancellationAsFailureOrStartTheNextItem() = runBlocking {
        val visited = mutableListOf<String>()
        try {
            refreshNativeProfileBatch(listOf("a", "cancel", "never"), { id ->
                visited += id
                if (id == "cancel") throw CancellationException("cancelled")
            }) { _, _ -> fail("Cancellation is not a subscription failure") }
            fail("Expected cancellation")
        } catch (error: CancellationException) { assertEquals("cancelled", error.message) }
        assertEquals(listOf("a", "cancel"), visited)
    }

    @Test fun emptyBatchDoesNoWork() = runBlocking {
        assertEquals(0, refreshNativeProfileBatch(emptyList(), { fail("Unexpected refresh") }) { _, _ -> fail("Unexpected failure") })
    }

    @Test fun subscriptionChangeIsSilentWhenResponseIsIdentical() {
        assertEquals(NativeSubscriptionRefreshChange(false, false),
            classifySubscriptionRefresh("server-a", "server-a", listOf("name", 100L), listOf("name", 100L)))
    }

    @Test fun quotaAndExpiryChangesDoNotRequireVpnReconnect() {
        assertEquals(NativeSubscriptionRefreshChange(true, false),
            classifySubscriptionRefresh("server-a", "server-a", listOf(100L, "expiry-1"), listOf(200L, "expiry-2")))
    }

    @Test fun serverChangesRequireVpnReconnect() {
        assertEquals(NativeSubscriptionRefreshChange(true, true),
            classifySubscriptionRefresh("server-a", "server-b", listOf(100L), listOf(100L)))
    }

    @Test fun configurationAndQuotaChangesAreBothDetected() {
        assertEquals(NativeSubscriptionRefreshChange(true, true),
            classifySubscriptionRefresh("server-a", "server-b", listOf(100L), listOf(200L)))
    }
}
