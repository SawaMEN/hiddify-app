package com.hiddify.hiddify

import com.hiddify.hiddify.nativerouting.NativeTimedSnapshotCache
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class NativeTimedSnapshotCacheTest {
    @Test fun expiresAtBoundaryAndSeparatesLocaleKeys() {
        var now = 0L
        var loads = 0
        val cache = NativeTimedSnapshotCache<String, Int>(30) { now }
        fun get(key: String = "en") = cache.get(key) { ++loads }
        assertEquals(1, get())
        now = 29
        assertEquals(1, get())
        now = 30
        assertEquals(2, get())
        assertEquals(3, get("ru"))
        assertEquals(4, get()) // Only one snapshot is retained.
        now = 0 // A backwards clock must not make a stale entry immortal.
        assertEquals(5, get())
        cache.invalidate()
        assertEquals(6, get())
    }

    @Test fun failedLoadsAreRetriedAndLifetimeStartsAfterLoading() {
        var now = 0L
        val cache = NativeTimedSnapshotCache<String, String>(30) { now }
        assertThrows(IllegalStateException::class.java) { cache.get("key") { error("failed") } }
        assertEquals("ok", cache.get("key") { now = 100; "ok" })
        now = 129
        assertEquals("ok", cache.get("key") { error("should be cached") })
        now = 130
        assertEquals("new", cache.get("key") { "new" })
    }

    @Test fun concurrentCallersShareOneSuccessfulLoad() {
        val pool = Executors.newFixedThreadPool(6)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val loads = AtomicInteger()
        val cache = NativeTimedSnapshotCache<String, Any>(30) { 0 }
        val expected = Any()
        try {
            val futures = (1..6).map {
                pool.submit<Any> { cache.get("key") {
                    loads.incrementAndGet()
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    expected
                } }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            release.countDown()
            futures.forEach { assertSame(expected, it.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, loads.get())
        } finally { release.countDown(); pool.shutdownNow() }
    }

    @Test fun invalidationDoesNotBlockAndRejectsAnInFlightSnapshot() {
        val pool = Executors.newFixedThreadPool(2)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cache = NativeTimedSnapshotCache<String, String>(30) { 0 }
        try {
            val loading = pool.submit<String> { cache.get("key") {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                "before change"
            } }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            pool.submit { cache.invalidate() }.get(5, TimeUnit.SECONDS)
            release.countDown()
            assertEquals("before change", loading.get(5, TimeUnit.SECONDS))
            assertEquals("after change", cache.get("key") { "after change" })
            assertEquals("after change", cache.get("key") { error("should be cached") })
        } finally { release.countDown(); pool.shutdownNow() }
    }
}
