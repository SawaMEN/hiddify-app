package com.hiddify.hiddify.nativeconnection

import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Covers Stop reaching Go just before Start enters and installs its cancellation context. */
internal object NativeStartupCancellation {
    suspend fun cancelWhileStarting(isStarting: () -> Boolean, stopNative: () -> Unit) {
        while (isStarting()) {
            stopNative()
            if (isStarting()) delay(50)
        }
    }
}

/** User stop, startup deadline and destruction can request cancellation concurrently. */
internal class NativeStartupCancellationTask(private val scope: CoroutineScope) {
    private val lock = Any()
    private var pending: Job? = null

    fun request(isStarting: () -> Boolean, stopNative: () -> Unit) {
        val next = synchronized(lock) {
            // A LAZY job is not active yet, but must already exclude another requester.
            if (pending?.isCompleted == false || !isStarting()) return
            scope.launch(start = CoroutineStart.LAZY) {
                NativeStartupCancellation.cancelWhileStarting(isStarting, stopNative)
            }.also { pending = it }
        }
        next.start()
    }

    /** Call after startup has left its critical section, before close/setup/replacement. */
    suspend fun join() {
        val current = synchronized(lock) { pending }
        current?.join()
    }
}

/** A core that never finishes startup must not leave Android stuck in Connecting. */
internal object NativeStartupDeadline {
    const val TIMEOUT_MS = 90_000L

    fun shouldAbort(starting: Boolean, stopRequested: Boolean, connectionDesired: Boolean, ownsCore: Boolean): Boolean =
        starting && !stopRequested && connectionDesired && ownsCore
}
