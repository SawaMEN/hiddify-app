package com.hiddify.hiddify.nativecore

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal data class NativeOutboundUpdate(val group: NativeOutboundGroup? = null, val error: String? = null, val session: Long = 0)

/**
 * Short-lived transport failures after a valid snapshot are recoverable by reopening
 * the stream. Show a warning after three consecutive failures; do not hide a real
 * first-load failure or a non-transport error.
 */
internal fun shouldReportOutboundsStreamError(
    hasSnapshot: Boolean,
    consecutiveFailures: Int,
    message: String,
): Boolean {
    if (!hasSnapshot || consecutiveFailures >= 3) return true
    val normalized = message.lowercase(java.util.Locale.ROOT)
    val interruptedGrpc = normalized.contains("grpc transport failure") &&
        normalized.contains("grpc-status=null")
    val normalStreamEnd = normalized.contains("proxy updates stream closed")
    return !interruptedGrpc && !normalStreamEnd
}

/** One primary-group stream shared by the list and foreground ranking. */
@OptIn(ExperimentalCoroutinesApi::class)
internal object NativePrimaryOutboundsFeed {
    private val generation = MutableStateFlow(0L)
    fun reset() { generation.update { it + 1 } }
    fun isCurrent(update: NativeOutboundUpdate): Boolean = update.session == generation.value
    val updates = generation.flatMapLatest { session ->
        flow {
            emit(NativeOutboundUpdate(session = session))
            var failures = 0
            var hasSnapshot = false
            while (true) {
                try {
                    NativeOutboundsRepository().watchPrimary().collect { group ->
                        failures = 0
                        if (group != null) hasSnapshot = true
                        emit(NativeOutboundUpdate(group, session = session))
                    }
                    throw java.io.IOException("Proxy updates stream closed")
                } catch (cancelled: CancellationException) { throw cancelled }
                  catch (error: Exception) {
                    failures = (failures + 1).coerceAtMost(4)
                    val description = error.message ?: "Proxy updates are unavailable"
                    if (shouldReportOutboundsStreamError(hasSnapshot, failures, description)) {
                        emit(NativeOutboundUpdate(error = description, session = session))
                    }
                    delay((1000L shl (failures - 1)).coerceAtMost(8000))
                }
            }
        }
    }.stateIn(CoroutineScope(SupervisorJob() + Dispatchers.IO),
        SharingStarted.WhileSubscribed(5000, 0), NativeOutboundUpdate())
}
