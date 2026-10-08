package com.hiddify.hiddify.nativeconnection

import org.junit.Assert.*
import org.junit.Test

class NativeServerHistoryMergeTest {
    @Test fun lateOldActivityKeepsNewerSessionsTagsAndMeasurements() {
        val previous = mapOf("a" to NativeServerSample(20.0, 3, 0, 200), "b" to NativeServerSample(30.0, 3, 0, 300))
        val result = mergeNativeServerHistory(previous, mapOf("a" to NativeServerSample(100.0, 1, 0, 100)))
        assertEquals(previous, result)
    }
    @Test fun retentionKeepsThe128MostRecentSamples() {
        val old = (1..128).associate { "$it" to NativeServerSample(20.0, 3, 0, it.toLong()) }
        val result = mergeNativeServerHistory(old, mapOf("new" to NativeServerSample(30.0, 3, 0, 200)))
        assertEquals(128, result.size)
        assertFalse(result.containsKey("1"))
        assertTrue(result.containsKey("new"))
    }
}
