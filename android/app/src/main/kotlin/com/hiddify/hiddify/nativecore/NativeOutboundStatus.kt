package com.hiddify.hiddify.nativecore

/** Numeric values are the upstream hcore wire contract; unknown values remain untested. */
enum class NativeOutboundStatus(val value: Int) {
    READY(0), CONNECTING(1), RECONNECTING(2), SEARCHING(3), FAILED(4), REGISTERING(5), UNKNOWN(-1);
    companion object {
        fun fromValue(value: Int) = entries.firstOrNull { it.value == value } ?: UNKNOWN
    }
}

enum class NativeProxyRowState { READY, UNTESTED, PING_FAILED, CONNECTING, REGISTERING, SEARCHING, RECONNECTING, FAILED }

fun NativeOutbound.rowState(): NativeProxyRowState = when (status) {
    NativeOutboundStatus.CONNECTING -> NativeProxyRowState.CONNECTING
    NativeOutboundStatus.RECONNECTING -> NativeProxyRowState.RECONNECTING
    NativeOutboundStatus.SEARCHING -> NativeProxyRowState.SEARCHING
    NativeOutboundStatus.REGISTERING -> NativeProxyRowState.REGISTERING
    NativeOutboundStatus.FAILED -> NativeProxyRowState.FAILED
    NativeOutboundStatus.UNKNOWN -> NativeProxyRowState.UNTESTED
    NativeOutboundStatus.READY -> when (delayMs) {
        in 1..65000 -> NativeProxyRowState.READY
        0 -> NativeProxyRowState.UNTESTED
        else -> NativeProxyRowState.PING_FAILED
    }
}

private val statusMark = Regex("\\s+(⏳|⌛|🔍|❌|🔑)\\s.*$")
fun NativeOutbound.typeWithoutStatus(): String =
    if (status == NativeOutboundStatus.READY) type else type.replace(statusMark, "")

/** Previous-run geolocation must not appear as a current lookup. */
fun NativeOutbound.currentIpInfo(): NativeOutboundIpInfo? = ipInfo?.takeUnless { it.fromCache }

fun NativeOutbound.signalBars(): Int = if (rowState() != NativeProxyRowState.READY) 0 else when {
    delayMs < 300 -> 4
    delayMs < 800 -> 3
    delayMs < 1500 -> 2
    else -> 1
}

/** Resolve a selected balancer through its own group; never treat the group as a tested server. */
fun activeNativeOutbound(groups: List<NativeOutboundGroup>, hop: NativeOutbound?): NativeOutbound? {
    val main = groups.firstOrNull() ?: return null
    val picked = main.items.firstOrNull() ?: return null
    val server = if (picked.isGroup) groups.firstOrNull { it.tag == picked.tag }?.items?.firstOrNull() ?: picked else picked
    val fastest = groups.firstOrNull { it.tag == "lowest" }?.items?.firstOrNull()
    return server.copy(chainHop = hop, mainGroupTag = main.tag,
        offerLowest = !picked.isGroup && main.selectable && server.rowState() in
            setOf(NativeProxyRowState.FAILED, NativeProxyRowState.PING_FAILED) &&
            fastest?.rowState() == NativeProxyRowState.READY)
}
