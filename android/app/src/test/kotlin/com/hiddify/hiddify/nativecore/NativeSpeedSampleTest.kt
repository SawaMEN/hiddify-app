package com.hiddify.hiddify.nativecore

import org.junit.Assert.*
import org.junit.Test

class NativeSpeedSampleTest {
    @Test fun unitsAreMegabitsPerSecond() {
        assertEquals(8.0, NativeSpeedSample(1_000_000, 1_000_000_000).validated().megabitsPerSecond, 0.000001)
    }
    @Test fun nanosecondTimingDoesNotRoundFastTransfersToZero() {
        assertEquals(800.0, NativeSpeedSample(100_000, 1_000_000).validated().megabitsPerSecond, 0.000001)
    }
    @Test fun invalidAndInsufficientSamplesAreRejected() {
        for (s in listOf(NativeSpeedSample(1, 1), NativeSpeedSample(65536, 0), NativeSpeedSample(65536, -1),
            NativeSpeedSample(NativeSpeedSample.MAX_DOWNLOAD + 1, 100))) {
            assertTrue(runCatching { s.validated() }.isFailure)
        }
    }
}
