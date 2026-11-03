package com.hiddify.hiddify.nativecore

import org.junit.Assert.*
import org.junit.Test

class NativeOutboundStatusTest {
    private fun node(tag: String = "server", delay: Int = 100, status: NativeOutboundStatus = NativeOutboundStatus.READY,
        group: Boolean = false) = NativeOutbound(tag, tag, "WARP", false, true, delay, "", 0, 0, 0, null,
        isGroup = group, status = status)
    private fun group(tag: String, vararg nodes: NativeOutbound) = NativeOutboundGroup(tag, "selector", "", true, nodes.toList())

    @Test fun liveCoreStatusTakesPriorityOverAnOldSuccessfulPing() {
        val expected = mapOf(NativeOutboundStatus.CONNECTING to NativeProxyRowState.CONNECTING,
            NativeOutboundStatus.REGISTERING to NativeProxyRowState.REGISTERING,
            NativeOutboundStatus.SEARCHING to NativeProxyRowState.SEARCHING,
            NativeOutboundStatus.RECONNECTING to NativeProxyRowState.RECONNECTING,
            NativeOutboundStatus.FAILED to NativeProxyRowState.FAILED)
        expected.forEach { (status, state) -> assertEquals(state, node(status = status).rowState()) }
        assertEquals(NativeProxyRowState.UNTESTED, node(status = NativeOutboundStatus.fromValue(123)).rowState())
    }

    @Test fun absentStatusUsesLegacyPingWithoutClaimingAnUntestedProxyIsReady() {
        assertEquals(NativeProxyRowState.UNTESTED, node(delay = 0).rowState())
        assertEquals(NativeProxyRowState.READY, node(delay = 65000).rowState())
        assertEquals(NativeProxyRowState.PING_FAILED, node(delay = 65001).rowState())
        assertEquals(NativeProxyRowState.PING_FAILED, node(delay = -1).rowState())
        assertEquals(0, node(status = NativeOutboundStatus.FAILED).signalBars())
    }

    @Test fun cacheIsNotCurrentGeolocationAndDisplayRemovesOnlyTheCoreSuffix() {
        val value = node().copy(ipInfo = NativeOutboundIpInfo(ip = "192.0.2.1", fromCache = true))
        assertNull(value.currentIpInfo())
        assertNotNull(value.copy(ipInfo = value.ipInfo!!.copy(fromCache = false)).currentIpInfo())
        assertEquals("WARP", node(status = NativeOutboundStatus.CONNECTING).copy(type = "WARP ⏳ Connecting...").typeWithoutStatus())

    }

    @Test fun fastestOfferRequiresADeadManualSelectionAndARealReadyAlternative() {
        val main = group("main", node(delay = 65001))
        assertTrue(activeNativeOutbound(listOf(main, group("lowest", node("fast"))), null)!!.offerLowest)
        assertFalse(activeNativeOutbound(listOf(main), null)!!.offerLowest)
        assertFalse(activeNativeOutbound(listOf(main, group("lowest", node("fast", status = NativeOutboundStatus.SEARCHING))), null)!!.offerLowest)
        assertFalse(activeNativeOutbound(listOf(group("main", node("balance", 65001, group = true)),
            group("lowest", node("fast"))), null)!!.offerLowest)
    }

    @Test fun activeBalancerResolvesTheActualChildAndPreservesHopFromSameMessage() {
        val hop = node("hop")
        val value = activeNativeOutbound(listOf(group("main", node("balance", group = true)),
            group("balance", node("actual"))), hop)!!
        assertEquals("actual", value.tag)
        assertEquals("main", value.mainGroupTag)
        assertEquals(hop, value.chainHop)
        assertFalse(value.offerLowest)
        assertNull(activeNativeOutbound(emptyList(), hop))
    }
}
