package com.hiddify.hiddify.nativeconnection

import kotlinx.coroutines.delay

/** Covers Stop reaching Go just before Start enters and installs its cancellation context. */
internal object NativeStartupCancellation {
    suspend fun cancelWhileStarting(isStarting: () -> Boolean, stopNative: () -> Unit) {
        while (isStarting()) {
            stopNative()
            if (isStarting()) delay(50)
        }
    }
}

/** A core that never finishes startup must not leave Android stuck in Connecting. */
internal object NativeStartupDeadline {
    const val TIMEOUT_MS = 90_000L

    fun shouldAbort(starting: Boolean, stopRequested: Boolean, connectionDesired: Boolean, ownsCore: Boolean): Boolean =
        starting && !stopRequested && connectionDesired && ownsCore
}
