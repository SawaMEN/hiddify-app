package com.hiddify.hiddify.bg

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One terminal result per Android DNS query, including cancellation and late callbacks. */
internal object NativeDnsQuery {
    suspend fun <T : Any> await(
        cancel: () -> Unit,
        reportCancellationFailure: (Exception) -> Unit,
        start: (success: (T) -> Unit, failure: (Exception) -> Unit) -> Unit,
    ): T = suspendCancellableCoroutine { continuation ->
        val finished = AtomicBoolean(false)
        val cancellationIssued = AtomicBoolean(false)
        fun cancelSafely() {
            if (!cancellationIssued.compareAndSet(false, true)) return
            try { cancel() }
            catch (error: Exception) {
                // invokeOnCancellation must not throw into Go's cancellation callback or
                // the JVM uncaught handler when Android/OEM signal cleanup fails.
                try { reportCancellationFailure(error) } catch (_: Exception) { }
            }
        }
        continuation.invokeOnCancellation {
            finished.set(true)
            cancelSafely()
        }
        try {
            if (continuation.isActive) start(
                { result -> if (finished.compareAndSet(false, true)) continuation.resume(result) },
                { error -> if (finished.compareAndSet(false, true)) continuation.resumeWithException(error) },
            )
        } catch (error: Exception) {
            // Claim completion before cancelling: signal cleanup can synchronously invoke
            // a callback, which must not replace the original startup failure.
            if (finished.compareAndSet(false, true)) {
                cancelSafely()
                continuation.resumeWithException(error)
            }
        }
    }
}
