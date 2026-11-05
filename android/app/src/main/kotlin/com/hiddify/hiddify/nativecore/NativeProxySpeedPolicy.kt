package com.hiddify.hiddify.nativecore

/** Pure selection and traffic-budget policy; measurements are supplied only by the core. */
object NativeProxySpeedPolicy {
    const val MB = 1_000_000L
    val sizes = listOf(MB, 5 * MB, 10 * MB, 50 * MB)
    fun validSize(bytes: Long): Long {
        require(bytes in sizes) { "Choose 1, 5, 10 or 50 MB per proxy" }
        return bytes
    }
    fun estimateBytes(bytes: Long, count: Int, upload: Boolean, download: Boolean): Long {
        require(count >= 0 && (upload || download)) { "Choose a test direction" }
        return validSize(bytes) * count * (if (upload && download) 2 else 1)
    }
    fun targets(items: List<NativeOutbound>, count: Int = 0): List<NativeOutbound> {
        require(count >= 0)
        val proxies = items.filter { !it.isGroup && it.visible && "§hide§" !in it.tag }.distinctBy { it.tag }
        val sorted = proxies.sortedWith(compareBy<NativeOutbound> {
            if (it.rowState() == NativeProxyRowState.READY) 0 else 1
        }.thenBy { if (it.delayMs in 1..65534) it.delayMs else Int.MAX_VALUE }.thenBy { it.tag })
        return if (count == 0) sorted else sorted.take(count)
    }
    fun compareRates(a: Long, b: Long): Int = b.coerceAtLeast(0).compareTo(a.coerceAtLeast(0))
}
