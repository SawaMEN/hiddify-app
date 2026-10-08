package com.hiddify.hiddify

import com.hiddify.hiddify.nativecore.NativeOutbound
import com.hiddify.hiddify.nativecore.presentNativeOutbounds
import com.hiddify.hiddify.nativepreferences.NativeOutboundSort
import org.junit.Assert.*
import org.junit.Test

class NativeOutboundPresentationTest {
    private fun proxy(tag: String, delay: Int = 0, group: Boolean = false, upload: Long = 0, download: Long = 0) =
        NativeOutbound(tag, "Display $tag", "vless", false, true, delay, "example.com", 443,
            upload, download, null, isGroup = group)

    @Test fun unsortedSearchPreservesCoreOrderAndMatchesTagOrProtocol() {
        val items = listOf(proxy("Z"), proxy("a"), proxy("B"))
        assertEquals(items, presentNativeOutbounds(items, "", NativeOutboundSort.UNSORTED))
        assertEquals(items, presentNativeOutbounds(items, "VLESS", NativeOutboundSort.UNSORTED))
        assertEquals(listOf("a"), presentNativeOutbounds(items, "A", NativeOutboundSort.UNSORTED).map { it.tag })
        assertTrue(presentNativeOutbounds(items, "Display", NativeOutboundSort.NAME).isEmpty())
    }

    @Test fun sortedModesKeepGroupsBeforeIndividualServers() {
        val items = listOf(proxy("a", delay = 10, download = 100), proxy("z-group", group = true))
        for (mode in listOf(NativeOutboundSort.NAME, NativeOutboundSort.DELAY, NativeOutboundSort.USAGE)) {
            assertEquals("z-group", presentNativeOutbounds(items, "", mode).first().tag)
        }
    }

    @Test fun delaySortKeepsTimeoutBeforeUnknownAndOrdersUnknownByTag() {
        val items = listOf(proxy("z"), proxy("timeout", 65535), proxy("fast", 20), proxy("a"))
        assertEquals(listOf("fast", "timeout", "a", "z"),
            presentNativeOutbounds(items, "", NativeOutboundSort.DELAY).map { it.tag })
    }

    @Test fun usageSortDoesNotOverflowLongCounters() {
        val items = listOf(proxy("small", download = 42), proxy("large", upload = Long.MAX_VALUE, download = Long.MAX_VALUE))
        assertEquals(listOf("large", "small"), presentNativeOutbounds(items, "", NativeOutboundSort.USAGE).map { it.tag })
        assertEquals(listOf("small", "large"), items.map { it.tag })
    }
    @Test fun scrollingKeepsExistingRowsStableAndAppendsNewRows() {
        val items = listOf(proxy("new"), proxy("b"), proxy("a"))
        assertEquals(listOf("a", "b", "new"),
            com.hiddify.hiddify.nativecore.keepNativeOutboundOrder(items, listOf("removed", "a", "b")).map { it.tag })
    }
}
