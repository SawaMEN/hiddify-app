package com.hiddify.hiddify

import com.hiddify.hiddify.nativecore.NativeOutbound
import com.hiddify.hiddify.nativecore.nameWithDetour
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
        assertEquals(listOf("B", "Z", "a"),
            presentNativeOutbounds(items, "Display", NativeOutboundSort.NAME).map { it.tag })
    }

    @Test fun detourShowsOnlyUserProxyAndIsSearchable() {
        val throughProxy = proxy("home").copy(detour = "Exit socks § 0")
        val internal = proxy("hidden").copy(detour = "Unblocker · Psiphon §hide§")
        val without = proxy("direct")
        assertEquals("Display home → Exit socks", throughProxy.nameWithDetour())
        assertEquals("Display hidden", internal.nameWithDetour())
        assertEquals("Display direct", without.nameWithDetour())
        val search = presentNativeOutbounds(listOf(throughProxy, internal, without), "Exit socks", NativeOutboundSort.UNSORTED)
        assertEquals(listOf("home"), search.map { it.tag })
        assertTrue(presentNativeOutbounds(listOf(internal), "Psiphon", NativeOutboundSort.UNSORTED).isEmpty())
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

    @Test fun usageMatchesExactSumsAcrossSignedBoundaryAndNegativeCounters() {
        val random = java.util.Random(17)
        val items = (0..500).map { index -> proxy("p$index", upload = random.nextLong(), download = random.nextLong()) } +
            listOf(proxy("max", upload = Long.MAX_VALUE, download = Long.MAX_VALUE),
                proxy("signed", upload = Long.MAX_VALUE, download = 1), proxy("zero", upload = -1, download = -1))
        val expected = items.sortedByDescending {
            java.math.BigInteger.valueOf(it.upload.coerceAtLeast(0)) + java.math.BigInteger.valueOf(it.download.coerceAtLeast(0))
        }
        assertEquals(expected, presentNativeOutbounds(items, "", NativeOutboundSort.USAGE))
    }

    @Test fun scrollingMatchesStableSortIncludingDuplicatesAndRemovedRows() {
        val random = java.util.Random(18)
        repeat(100) {
            val items = (0..100).map { proxy("p${random.nextInt(30)}", delay = it) }
            val order = (0..50).map { "p${random.nextInt(40)}" }
            val positions = order.withIndex().associate { it.value to it.index }
            assertEquals(items.sortedBy { positions[it.tag] ?: Int.MAX_VALUE },
                com.hiddify.hiddify.nativecore.keepNativeOutboundOrder(items, order))
        }
    }

    @Test fun duplicateTagsUseTheirOwnTrafficCounters() {
        val first = proxy("same", download = 1)
        val second = proxy("same", download = 100)
        val middle = proxy("other", download = 50)
        assertEquals(listOf(second, middle, first),
            presentNativeOutbounds(listOf(first, second, middle), "", NativeOutboundSort.USAGE))
    }
}
