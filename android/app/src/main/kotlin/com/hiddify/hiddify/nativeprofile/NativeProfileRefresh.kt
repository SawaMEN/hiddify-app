package com.hiddify.hiddify.nativeprofile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Quota and expiry updates are user-visible but do not require restarting the VPN. */
internal data class NativeSubscriptionRefreshChange(val changed: Boolean, val configChanged: Boolean)

internal fun classifySubscriptionRefresh(
    previousConfig: String?,
    newConfig: String?,
    previousMetadata: List<Any?>,
    newMetadata: List<Any?>,
): NativeSubscriptionRefreshChange {
    val configChanged = previousConfig != newConfig
    return NativeSubscriptionRefreshChange(
        changed = configChanged || previousMetadata != newMetadata,
        configChanged = configChanged,
    )
}

/** Keep the cancellation waiter separate from blocking HTTP/JNI work, and join its cleanup. */
internal suspend fun <T> runNativeProfileRefresh(
    onCancellationFailure: (Exception) -> Unit = {},
    refresh: (NativeProfileImportCancellation) -> T,
): T = coroutineScope {
    val cancellation = NativeProfileImportCancellation()
    val worker = async(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        cancellation.ensureActive()
        refresh(cancellation)
    }
    try {
        worker.await()
    } finally {
        // Once beginCommit has run, let the existing atomic file/DB transaction finish.
        try { cancellation.cancel() }
        catch (error: Exception) { onCancellationFailure(error) }
    }
}

/** A persistent 3-day failure window suspends automatic refreshes, never manual retries. */
internal object NativeAutoRefreshFailurePolicy {
    const val SUSPEND_AFTER_MS: Long = 3L * 24 * 60 * 60 * 1000

    fun firstFailureAt(previous: Long, now: Long): Long =
        if (previous > 0L) previous else now.coerceAtLeast(1L)

    fun isSuspended(firstFailureAt: Long, now: Long): Boolean =
        firstFailureAt > 0L && now >= firstFailureAt && now - firstFailureAt >= SUSPEND_AFTER_MS
}

/** Ordinary failures do not discard earlier successes; cancellation never starts another item. */
internal suspend fun refreshNativeProfileBatch(
    ids: List<String>,
    refresh: suspend (String) -> Unit,
    onFailure: (String, Exception) -> Unit,
): Int {
    var updated = 0
    for (id in ids) {
        currentCoroutineContext().ensureActive()
        try {
            refresh(id)
            updated++
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            onFailure(id, error)
        }
    }
    return updated
}
