package com.hiddify.hiddify.nativeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeConnectionMotionLayerTest {
    @Test fun idleAndInvalidTrafficNeverIlluminate() {
        assertEquals(0f, nativeMotionTrafficLevel(0L), 0f)
        assertEquals(0f, nativeMotionTrafficLevel(-100L), 0f)
        assertEquals(0f, nativeMotionTrafficLevel(Long.MIN_VALUE), 0f)
    }

    @Test fun growingRatesHaveBoundedMonotonicIntensity() {
        val values = listOf(1L, 100L, 500L, 1024L, 10_000L, 150_000L, 1_000_000L, Long.MAX_VALUE)
            .map(::nativeMotionTrafficLevel)
        assertTrue(values.first() > 0f)
        assertTrue(values.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(values.all { it in 0f..1f })
        assertEquals(1f, values.last(), 0f)
    }
}
