package com.hiddify.hiddify.nativeconnection

import com.hiddify.hiddify.constant.Status

/** One visual state for the home glow and connection button, including stale saved settings. */
internal enum class NativeConnectionTone { DISCONNECTED, CONNECTED, PENDING, FAILED }

internal object NativeConnectionPresentation {
    fun tone(status: Status, recovering: Boolean, reconnecting: Boolean, failed: Boolean,
        requiresReconnect: Boolean): NativeConnectionTone = when {
        status == Status.Starting || status == Status.Stopping || recovering || reconnecting -> NativeConnectionTone.PENDING
        failed -> NativeConnectionTone.FAILED
        status == Status.Started && requiresReconnect -> NativeConnectionTone.PENDING
        status == Status.Started -> NativeConnectionTone.CONNECTED
        else -> NativeConnectionTone.DISCONNECTED
    }

    fun unexpectedStop(previous: Status, next: Status, connectionDesired: Boolean): Boolean =
        previous == Status.Started && next == Status.Stopped && connectionDesired
}
