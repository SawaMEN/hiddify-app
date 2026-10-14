package com.hiddify.hiddify.nativeconnection

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler

/** A supervised service task still needs a last-resort exception boundary. */
internal class NativeServiceFailureBoundary(
    private val onFailure: (Exception) -> Unit,
    private val onFatal: (Throwable) -> Unit,
) : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {
    private val stopping = AtomicBoolean(false)

    fun reset() { stopping.set(false) }

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        when {
            exception is CancellationException -> Unit
            // VM/native linkage errors must still reach Android's crash handler.
            exception !is Exception -> onFatal(exception)
            stopping.compareAndSet(false, true) -> onFailure(exception)
        }
    }
}
