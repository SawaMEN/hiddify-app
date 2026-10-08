package com.hiddify.hiddify.nativecore

import com.hiddify.hiddify.nativepreferences.NativeOutboundSort
import java.math.BigInteger

internal fun presentNativeOutbounds(items: List<NativeOutbound>, query: String, sort: NativeOutboundSort): List<NativeOutbound> {
    val filtered = items.filter { it.tag.contains(query, ignoreCase = true) || it.type.contains(query, ignoreCase = true) }
    if (sort == NativeOutboundSort.UNSORTED) return filtered
    val usage = if (sort == NativeOutboundSort.USAGE) filtered.associate { it.tag to traffic(it) } else emptyMap()
    return filtered.sortedWith(Comparator { a, b ->
        if (a.isGroup != b.isGroup) return@Comparator if (a.isGroup) -1 else 1
        when (sort) {
            NativeOutboundSort.NAME -> a.tag.compareTo(b.tag)
            NativeOutboundSort.DELAY -> when {
                a.delayMs == 0 && b.delayMs == 0 -> a.tag.compareTo(b.tag)
                a.delayMs == 0 -> 1
                b.delayMs == 0 -> -1
                else -> a.delayMs.compareTo(b.delayMs)
            }
            NativeOutboundSort.USAGE -> usage.getValue(b.tag).compareTo(usage.getValue(a.tag))
            NativeOutboundSort.UNSORTED -> 0
        }
    })
}

private fun traffic(outbound: NativeOutbound): BigInteger =
    BigInteger.valueOf(outbound.upload.coerceAtLeast(0)).add(BigInteger.valueOf(outbound.download.coerceAtLeast(0)))

/** Keep visible rows in place during a drag; new entries are appended until scrolling stops. */
internal fun keepNativeOutboundOrder(items: List<NativeOutbound>, order: List<String>): List<NativeOutbound> {
    val positions = order.withIndex().associate { it.value to it.index }
    return items.sortedBy { positions[it.tag] ?: Int.MAX_VALUE }
}
