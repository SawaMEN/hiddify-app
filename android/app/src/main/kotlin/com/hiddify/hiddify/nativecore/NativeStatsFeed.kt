package com.hiddify.hiddify.nativecore

import android.os.SystemClock
import com.hiddify.hiddify.bg.BoxService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal object NativeStatsFeed {
    private val sampler = NativeStatsStream(CoroutineScope(SupervisorJob() + Dispatchers.IO),
        load = { NativeStatsRepository().load() }, connected = { BoxService.isStarted() }, clock = SystemClock::elapsedRealtime)
    val snapshots = sampler.snapshots
    fun reset() = sampler.reset()
}
