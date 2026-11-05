package com.hiddify.hiddify.nativecore

import com.hiddify.hiddify.nativepreferences.NativeOutboundSort
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows

class NativeProxySpeedPolicyTest {
    private fun proxy(tag: String, delay: Int, group: Boolean = false, visible: Boolean = true) =
        NativeOutbound(tag, tag, "socks", false, visible, delay, "example.invalid", 443, 0, 0, null, isGroup = group)

    @Test fun budgetIncludesBothDirectionsAndEveryProxy() {
        assertEquals(30_000_000L, NativeProxySpeedPolicy.estimateBytes(5_000_000, 3, true, true))
        assertThrows(IllegalArgumentException::class.java) { NativeProxySpeedPolicy.estimateBytes(1_000_000, 3, false, false) }
        assertThrows(IllegalArgumentException::class.java) { NativeProxySpeedPolicy.validSize(2_000_000) }
    }

    @Test fun fastestTargetsExcludeGroupsHiddenAndDuplicateTags() {
        val values = listOf(proxy("group", 1, group = true), proxy("hidden§hide§", 1),
            proxy("hidden", 1, visible = false), proxy("failed", 65535), proxy("untested", 0),
            proxy("slow", 200), proxy("fast", 40), proxy("fast", 50))
        assertEquals(listOf("fast", "slow"), NativeProxySpeedPolicy.targets(values, 2).map { it.tag })
        assertEquals(4, NativeProxySpeedPolicy.targets(values).size)
    }

    @Test fun cachedDelayInfluencesSortButDoesNotReplaceLiveLatency() {
        val cached = proxy("cached", 0).copy(sortDelayMs = 35)
        val measured = proxy("measured", 90).copy(sortDelayMs = 90)
        val unknown = proxy("unknown", 0)
        assertEquals(listOf("cached", "measured", "unknown"),
            presentNativeOutbounds(listOf(unknown, measured, cached), "", NativeOutboundSort.DELAY).map { it.tag })
        assertEquals(0, cached.delayMs) // An old ping is not displayed as a new measurement.
    }

    @Test fun speedSortingPrefersNewResultsBeforeCachedAndUsesOtherDirection() {
        val current = proxy("current", 65).copy(downloadSpeed = 1, uploadSpeed = 4)
        val cached = proxy("cached", 10).copy(downloadSpeed = 1000, downloadSpeedCached = true)
        val upload = proxy("upload", 8).copy(uploadSpeed = 200)
        val ping = proxy("ping", 20)
        val unknown = proxy("unknown", 0)
        assertEquals(listOf("current", "cached", "upload", "ping", "unknown"),
            presentNativeOutbounds(listOf(unknown, ping, upload, cached, current), "", NativeOutboundSort.DOWNLOAD)
                .map { it.tag })
        assertEquals(listOf("upload", "current", "cached", "ping", "unknown"),
            presentNativeOutbounds(listOf(unknown, ping, upload, cached, current), "", NativeOutboundSort.UPLOAD)
                .map { it.tag })
    }

    @Test fun speedSortingDoesNotTreatFailuresOrCachedDelaysAsFreshMeasurements() {
        val failed = proxy("failed", 0).copy(downloadSpeed = -1, sortDelayMs = 10)
        val untested = proxy("untested", 70)
        val valid = proxy("valid", 5)
        assertEquals(listOf("valid", "untested", "failed"),
            presentNativeOutbounds(listOf(failed, untested, valid), "", NativeOutboundSort.DOWNLOAD)
                .map { it.tag })
        assertEquals(10, failed.sortDelayMs)
    }

    @Test fun sortPutsMeasuredSpeedsBeforeFailuresAndUntestedProxies() {
        val slow = proxy("slow", 20).copy(downloadSpeed = 100, uploadSpeed = 1_000)
        val fast = proxy("fast", 40).copy(downloadSpeed = Long.MAX_VALUE, uploadSpeed = 100)
        val failed = proxy("failed", 5).copy(downloadSpeed = -1)
        assertEquals(listOf("fast", "slow", "failed"),
            presentNativeOutbounds(listOf(failed, slow, fast), "", NativeOutboundSort.DOWNLOAD).map { it.tag })
        assertEquals(listOf("slow", "fast", "failed"),
            presentNativeOutbounds(listOf(failed, slow, fast), "", NativeOutboundSort.UPLOAD).map { it.tag })
    }
}
