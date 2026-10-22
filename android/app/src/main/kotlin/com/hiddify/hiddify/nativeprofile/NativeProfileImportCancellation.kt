package com.hiddify.hiddify.nativeprofile

import java.util.concurrent.CancellationException

/** One import owns one token; cancellation must never affect other repository requests. */
class NativeProfileImportCancellation {
    private val lock = Any()
    private var cancelled = false
    private var committing = false
    private var cancelAfterCommit = false
    private var cancelRequest: (() -> Unit)? = null

    fun cancel(): Boolean {
        val request = synchronized(lock) {
            // Once file/database replacement begins, let the existing transaction finish.
            // A multi-file archive must still stop before attempting the next entry.
            if (committing) {
                cancelAfterCommit = true
                return false
            }
            if (cancelled) return false
            cancelled = true
            cancelRequest.also { cancelRequest = null }
        }
        request?.invoke()
        return true
    }

    fun ensureActive() = synchronized(lock) {
        if (cancelled) throw CancellationException("Profile import cancelled")
    }

    fun attachRequest(cancel: () -> Unit) {
        val cancelImmediately = synchronized(lock) {
            check(!committing) { "Import already committing" }
            if (cancelled) true else {
                check(cancelRequest == null) { "Import request already attached" }
                cancelRequest = cancel
                false
            }
        }
        if (cancelImmediately) {
            cancel()
            ensureActive()
        }
    }

    fun detachRequest() = synchronized(lock) { cancelRequest = null }

    fun beginCommit() = synchronized(lock) {
        ensureActive()
        check(cancelRequest == null) { "Import request still attached" }
        check(!committing) { "Import already committing" }
        committing = true
    }

    /**
     * Only archive imports call this after each completed atomic file/DB commit.
     * Cancellation during the transaction is deferred until the next entry.
     */
    fun finishArchiveEntry() = synchronized(lock) {
        check(committing) { "Profile entry was not committing" }
        committing = false
        if (cancelAfterCommit) {
            cancelled = true
            cancelAfterCommit = false
        }
    }
}
