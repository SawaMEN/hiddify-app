package com.hiddify.hiddify.nativecore

import com.hiddify.hiddify.nativepreferences.NativeOutboundSort

internal fun presentNativeOutbounds(items: List<NativeOutbound>, query: String, sort: NativeOutboundSort): List<NativeOutbound> {
    val filtered = if (query.isEmpty()) items else items.filter {
        it.tag.contains(query, ignoreCase = true) || it.type.contains(query, ignoreCase = true)
    }
    if (sort == NativeOutboundSort.UNSORTED) return filtered
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
            NativeOutboundSort.USAGE -> java.lang.Long.compareUnsigned(traffic(b), traffic(a))
            NativeOutboundSort.UNSORTED -> 0
        }
    })
}

// Each nonnegative counter is at most Long.MAX_VALUE; their exact sum fits in an
// unsigned 64-bit value. Compare unsigned so signed overflow cannot reverse the order.
private fun traffic(outbound: NativeOutbound): Long =
    outbound.upload.coerceAtLeast(0) + outbound.download.coerceAtLeast(0)

/** Keep visible rows in place during a drag; new entries are appended until scrolling stops. */
internal fun keepNativeOutboundOrder(items: List<NativeOutbound>, order: List<String>): List<NativeOutbound> {
    if (items.size < 2 || order.isEmpty()) return items
    val positions = HashMap<String, Int>(order.size)
    order.forEachIndexed { index, tag -> positions[tag] = index }
    val ranked = arrayOfNulls<NativeOutbound>(order.size)
    var duplicates: MutableMap<Int, MutableList<NativeOutbound>>? = null
    val unranked = ArrayList<NativeOutbound>()
    for (item in items) {
        val position = positions[item.tag]
        if (position == null) {
            unranked.add(item)
        } else if (ranked[position] == null) {
            ranked[position] = item
        } else {
            val extra = duplicates ?: HashMap<Int, MutableList<NativeOutbound>>().also { duplicates = it }
            extra.getOrPut(position) { ArrayList() }.add(item)
        }
    }
    return ArrayList<NativeOutbound>(items.size).apply {
        ranked.forEachIndexed { index, item ->
            if (item != null) add(item)
            duplicates?.get(index)?.let { addAll(it) }
        }
        addAll(unranked)
    }
}
