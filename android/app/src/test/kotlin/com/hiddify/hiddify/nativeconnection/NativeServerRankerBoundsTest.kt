package com.hiddify.hiddify.nativeconnection

import org.junit.Assert.*
import org.junit.Test

class NativeServerRankerBoundsTest {
    @Test fun restorationKeepsNewestValidSamplesInsteadOfFirstInsertedSamples() {
        val ranker = NativeServerRanker()
        val history = (1..130).associate { "server-$it" to NativeServerSample(100.0, 3, 0, it.toLong()) }
        ranker.restore(history, 1000)
        assertEquals(128, ranker.snapshot().size)
        assertTrue(ranker.snapshot().containsKey("server-130"))
        assertFalse(ranker.snapshot().containsKey("server-1"))
    }
    @Test fun malformedSamplesDoNotConsumeTheRestorationLimit() {
        val ranker = NativeServerRanker()
        val history = (1..128).associate { "bad-$it" to NativeServerSample(Double.NaN, 3, 0, 900) } +
            mapOf("good" to NativeServerSample(100.0, 3, 0, 800))
        ranker.restore(history, 1000)
        assertEquals(setOf("good"), ranker.snapshot().keys)
    }
    @Test fun liveObservationsBoundMemoryAndRejectInvalidIdentifiers() {
        val ranker = NativeServerRanker()
        ranker.observe("", 100, 1, 1000)
        ranker.observe("x".repeat(1025), 100, 1, 1000)
        assertTrue(ranker.snapshot().isEmpty())
        repeat(1000) { ranker.observe("server-$it", 100, it.toLong() + 1, 2000) }
        assertEquals(128, ranker.snapshot().size)
        assertTrue(ranker.snapshot().containsKey("server-999"))
    }
}
