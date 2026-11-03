package com.hiddify.hiddify.nativeconnection

import com.hiddify.hiddify.nativecore.*

enum class NativeChainStepState { UNKNOWN, WAITING, CONNECTING, SLOW, READY, FAILED }

/** Core status and measurements, ordered by the stage that is dialed first. */
object NativeChainStepPolicy {
    fun hop(hop: NativeOutbound?, server: NativeOutbound?, direction: String, waitedMs: Long): NativeChainStepState {
        if (direction == "off") return NativeChainStepState.UNKNOWN
        if (direction == "extra_security" && server?.rowState() != NativeProxyRowState.READY)
            return NativeChainStepState.WAITING
        // Missing telemetry on older cores is unknown, never a fabricated success or failure.
        if (hop == null || hop.status == NativeOutboundStatus.UNKNOWN) return NativeChainStepState.UNKNOWN
        if (hop.status == NativeOutboundStatus.FAILED) return NativeChainStepState.FAILED
        if (hop.rowState() == NativeProxyRowState.READY) return NativeChainStepState.READY
        return when {
            waitedMs >= 60_000 -> NativeChainStepState.FAILED
            waitedMs >= 15_000 -> NativeChainStepState.SLOW
            else -> NativeChainStepState.CONNECTING
        }
    }
}
