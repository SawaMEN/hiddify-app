package com.hiddify.hiddify.nativeprofile

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class NativeSubscriptionMetadataTest {
    private val now = LocalDateTime.parse("2026-10-08T12:00:00")

    @Test fun invalidIntervalsCannotWrapIntoNegativeSeconds() {
        assertNull(NativeSubscriptionMetadata.intervalSeconds(null))
        listOf(0L, -1L, Long.MAX_VALUE, Long.MAX_VALUE / 3600 + 1).forEach {
            assertNull(NativeSubscriptionMetadata.intervalSeconds(it))
        }
        assertEquals(43200L, NativeSubscriptionMetadata.intervalSeconds(12))
        val largest = Long.MAX_VALUE / 3600
        assertEquals(largest * 3600, NativeSubscriptionMetadata.intervalSeconds(largest))
    }

    @Test fun updatesBecomeDueAtTheExactInterval() {
        val last = now.minusHours(12).toString()
        assertTrue(NativeSubscriptionMetadata.isUpdateDue(last, 43200, now))
        assertFalse(NativeSubscriptionMetadata.isUpdateDue(last, 43201, now))
        assertFalse(NativeSubscriptionMetadata.isUpdateDue(now.plusHours(1).toString(), 1, now))
    }

    @Test fun hugeIntervalsDoNotOverflowDatesAndStopOtherUpdates() {
        assertFalse(NativeSubscriptionMetadata.isUpdateDue(now.minusDays(1).toString(), Long.MAX_VALUE, now))
        assertFalse(NativeSubscriptionMetadata.isUpdateDue(now.minusDays(1).toString(), -1, now))
        assertFalse(NativeSubscriptionMetadata.isUpdateDue("bad-date", null, now))
        assertTrue(NativeSubscriptionMetadata.isUpdateDue("bad-date", 3600, now))
    }

    @Test fun readsValidTrafficAndExpiration() {
        val usage = NativeSubscriptionMetadata.usage(" Upload = 10 ; DOWNLOAD=20; total=100; expire=1800000000")!!
        assertEquals(10L, usage.upload)
        assertEquals(20L, usage.download)
        assertEquals(100L, usage.total)
        assertEquals(1800000000L, usage.expireSeconds)
    }

    @Test fun badCountersAreIgnoredWithoutDiscardingTheConfiguration() {
        listOf(null, "", "upload=-1; download=2", "upload=1; download=-2", "upload=oops; download=2").forEach {
            assertNull(NativeSubscriptionMetadata.usage(it))
        }
    }

    @Test fun unlimitedAndOutOfRangeExpirationKeepLegacySentinels() {
        listOf("", "; total=0; expire=0", "; total=-1; expire=-1", "; expire=${Long.MAX_VALUE}").forEach {
            val usage = NativeSubscriptionMetadata.usage("upload=1; download=2$it")!!
            assertEquals(NativeSubscriptionMetadata.INFINITE_TRAFFIC, usage.total)
            assertEquals(NativeSubscriptionMetadata.INFINITE_EXPIRE_SECONDS, usage.expireSeconds)
        }
    }
}
