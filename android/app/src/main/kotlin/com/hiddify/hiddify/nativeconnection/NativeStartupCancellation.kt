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
