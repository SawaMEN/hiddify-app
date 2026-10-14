package com.hiddify.hiddify.nativeconnection

/** Literal IPv4 avoids treating a slow/blocked resolver or IPv6 route as a broken UDP tunnel. */
internal object NativeWarpProbePolicy {
    data class Target(val url: String, val seconds: Long)
    const val BUDGET_MS = 10_000L
    val targets = listOf(
        Target("https://1.1.1.1/cdn-cgi/trace", 7),
        Target("http://1.1.1.1/cdn-cgi/trace", 3),
    )
}
