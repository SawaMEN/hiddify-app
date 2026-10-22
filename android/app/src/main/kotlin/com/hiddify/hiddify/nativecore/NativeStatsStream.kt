package com.hiddify.hiddify.nativecore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn

/** One sampler regardless of the number of collectors; no work without subscribers. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class NativeStatsStream(
    scope: CoroutineScope,
    private val load: suspend () -> NativeSystemStats,
    private val connected: () -> Boolean,
    private val clock: () -> Long,
    private val intervalMillis: Long = 1000,
) {
    init { require(intervalMillis > 0) { "Statistics polling interval must be positive" } }
    private val session = MutableStateFlow(0L)
    fun reset() { session.update { it + 1 } }
    val snapshots = session.flatMapLatest { generation ->
        flow {
            val meter = NativeTrafficRateMeter()
            var lastGood: NativeSystemStats? = null
            emit(NativeSystemStats())
            while (true) {
                if (!connected()) {
                    meter.reset()
                    lastGood = null
                    emit(NativeSystemStats())
                } else {
                    val stats = try {
                        load()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // A temporary HTTP/2/gRPC failure is not a VPN disconnect.
                        // Retain the last known counters, but mark rates unavailable
                        // and re-baseline the next successful sample to avoid spikes.
                        meter.reset()
                        val stale = lastGood?.copy(speedAvailable = false, uplink = 0, downlink = 0)
                        currentCoroutineContext().ensureActive()
                        if (stale != null && generation == session.value && connected()) emit(stale)
                        null
                    }
                    currentCoroutineContext().ensureActive()
                    if (stats != null && generation == session.value && connected()) {
                        val sampled = meter.sample(stats, clock())
                        lastGood = sampled
                        emit(sampled)
                    }
                }
                delay(intervalMillis)
            }
        }
    }.flowOn(Dispatchers.IO).stateIn(scope, SharingStarted.WhileSubscribed(0, 0), NativeSystemStats())
}
