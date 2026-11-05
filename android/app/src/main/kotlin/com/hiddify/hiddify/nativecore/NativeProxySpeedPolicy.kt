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
    /** Upstream 5.5.5: prefer measurements from this run, then cached measurements,
     *  then the other direction, and finally a successful live ping.
     *  An untested (0) and failed (-1) speed are both unmeasured.
     */
    fun compareBySpeeds(a: NativeOutbound, b: NativeOutbound, uploadFirst: Boolean): Int {
        fun tier(rate: Long, cached: Boolean) = when {
            rate <= 0L -> 2
            cached -> 1
            else -> 0
        }
        fun bySpeed(x: Long, xCached: Boolean, y: Long, yCached: Boolean): Int {
            val byTier = tier(x, xCached).compareTo(tier(y, yCached))
            return if (byTier != 0) byTier else if (x > 0L) y.compareTo(x) else 0
        }
        val up = bySpeed(a.uploadSpeed, a.uploadSpeedCached, b.uploadSpeed, b.uploadSpeedCached)
        val down = bySpeed(a.downloadSpeed, a.downloadSpeedCached, b.downloadSpeed, b.downloadSpeedCached)
        val first = if (uploadFirst) up else down
        if (first != 0) return first
        val second = if (uploadFirst) down else up
        if (second != 0) return second
        val aAnswers = a.delayMs in 1..64999
        val bAnswers = b.delayMs in 1..64999
        if (aAnswers != bAnswers) return if (aAnswers) -1 else 1
        return if (aAnswers) a.delayMs.compareTo(b.delayMs) else 0
    }
}
