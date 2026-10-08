package com.hiddify.hiddify.nativecore

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class NativeTrafficStatsTest {
    private fun counters(up: Long = 0, down: Long = 0, profile: String = "one", available: Boolean = true) =
        NativeSystemStats(trafficAvailable = available, uplinkTotal = up, downlinkTotal = down, currentProfile = profile)

    @Test fun firstSampleNeedsBaseline() {
        val result = NativeTrafficRateMeter().sample(counters(4096, 8192), 1000)
        assertFalse(result.speedAvailable)
        assertTrue(result.trafficAvailable)
        assertEquals(0L, result.uplink)
    }
    @Test fun accountsForActualIntervalInsteadOfAssumingOneSecond() {
        val meter = NativeTrafficRateMeter()
        meter.sample(counters(500, 1000), 1000)
        val result = meter.sample(counters(2548, 9192), 3000)
        assertTrue(result.speedAvailable)
        assertEquals(1024L, result.uplink)
        assertEquals(4096L, result.downlink)
    }
    @Test fun idleIsAvailableZero() {
        val meter = NativeTrafficRateMeter()
        meter.sample(counters(100), 1000)
        val result = meter.sample(counters(100), 2000)
        assertTrue(result.speedAvailable)
        assertEquals(0L, result.uplink)
    }
    @Test fun eitherCounterResetInvalidatesBothRatesAndRebases() {
        val meter = NativeTrafficRateMeter()
        meter.sample(counters(1000, 1000), 1000)
        assertFalse(meter.sample(counters(2000, 0), 2000).speedAvailable)
        assertEquals(100L, meter.sample(counters(2100, 100), 3000).downlink)
    }
    @Test fun profileChangeDoesNotMixSessions() {
        val meter = NativeTrafficRateMeter()
        meter.sample(counters(100), 1000)
        assertFalse(meter.sample(counters(10000, profile = "two"), 2000).speedAvailable)
        assertEquals(100L, meter.sample(counters(10100, profile = "two"), 3000).uplink)
    }
    @Test fun unavailableResponseDiscardsBaseline() {
        val meter = NativeTrafficRateMeter()
        meter.sample(counters(), 1000)
        assertFalse(meter.sample(counters(available = false), 2000).trafficAvailable)
        assertFalse(meter.sample(counters(10000), 3000).speedAvailable)
    }
    @Test fun lifecycleResetRequiresFreshBaseline() {
        val meter = NativeTrafficRateMeter()
        meter.sample(counters(), 1000)
        meter.reset()
        assertFalse(meter.sample(counters(10000), 2000).speedAvailable)
    }
    @Test fun longPauseDoesNotShowStaleAverage() {
        val meter = NativeTrafficRateMeter()
        meter.sample(counters(), 1000)
        assertFalse(meter.sample(counters(10000), 12000).speedAvailable)
        assertEquals(100L, meter.sample(counters(10100), 13000).uplink)
    }
    @Test fun invalidClockAndCountersNeverProduceNegativeRates() {
        val meter = NativeTrafficRateMeter()
        meter.sample(counters(), 1000)
        assertFalse(meter.sample(counters(100), 1000).speedAvailable)
        assertFalse(meter.sample(counters(-1), 2000).trafficAvailable)
        assertFalse(meter.sample(counters(), -1).trafficAvailable)
    }
    @Test fun veryLargeCountersDoNotOverflowMultiplication() {
        val meter = NativeTrafficRateMeter()
        meter.sample(counters(), 1000)
        assertEquals(Long.MAX_VALUE, meter.sample(counters(Long.MAX_VALUE), 1001).uplink)
    }
    @Test fun formatterBoundsLargeNumbersAndUsesLocale() {
        assertEquals(NativeTrafficAmount("0", 0), nativeTrafficAmount(-1, Locale.US))
        assertEquals(NativeTrafficAmount("1023", 0), nativeTrafficAmount(1023, Locale.US))
        assertEquals(NativeTrafficAmount("1.0", 1), nativeTrafficAmount(1024, Locale.US))
        assertEquals(NativeTrafficAmount("1,5", 1), nativeTrafficAmount(1536, Locale.forLanguageTag("ru")))
        assertEquals(NativeTrafficAmount("1.0", 2), nativeTrafficAmount(1048575, Locale.US))
        assertEquals(6, nativeTrafficAmount(Long.MAX_VALUE, Locale.US).unitIndex)
    }
}
