package com.hiddify.hiddify.nativeconnection

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A failed traffic probe releases the old tunnel before the next endpoint is started.
 * Startup/persistence errors are not mistaken for a blocked peer. */
internal object NativeWarpStartupPolicy {
    suspend fun select(
        candidates: List<String>,
        checkDesired: () -> Unit,
        start: (String) -> Unit,
        probe: suspend () -> Boolean,
        stop: () -> Unit,
    ): Int? {
        for ((index, candidate) in candidates.withIndex()) {
            currentCoroutineContext().ensureActive()
            checkDesired()
            start(candidate)
            val healthy = probe()
            currentCoroutineContext().ensureActive()
            checkDesired()
            if (healthy) return index
            stop()
        }
        return null
    }
}
