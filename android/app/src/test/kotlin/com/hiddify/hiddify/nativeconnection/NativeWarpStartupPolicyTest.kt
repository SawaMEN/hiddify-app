package com.hiddify.hiddify.nativeconnection

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NativeWarpStartupPolicyTest {
    @Test fun onlyAcceptsTrafficAndStopsBeforeRestart() = runBlocking {
        val events = mutableListOf<String>()
        var attempts = 0
        val selected = NativeWarpStartupPolicy.select(listOf("a", "b", "c"), {},
            { events += "start:$it"; attempts++ }, { events += "probe"; attempts == 2 }, { events += "stop" })
        assertEquals(1, selected)
        assertEquals(listOf("start:a", "probe", "stop", "start:b", "probe"), events)
    }
    @Test fun allBlockedPeersLeaveNoRunningTunnel() = runBlocking {
        var starts = 0; var stops = 0
        assertNull(NativeWarpStartupPolicy.select(listOf("a", "b"), {}, { starts++ }, { false }, { stops++ }))
        assertEquals(2, starts); assertEquals(2, stops)
    }
    @Test fun cancellationDuringProbeNeverStartsNextPeerOrAcceptsSuccess() = runBlocking {
        var desired = true; var starts = 0
        try {
            NativeWarpStartupPolicy.select(listOf("a", "b"), { if (!desired) throw CancellationException() },
                { starts++ }, { desired = false; true }, {})
            fail("Cancellation must propagate to the service cleanup")
        } catch (_: CancellationException) { }
        assertEquals(1, starts)
    }
    @Test fun jniFailureIsNotTreatedAsNetworkFailure() = runBlocking {
        var starts = 0
        try {
            NativeWarpStartupPolicy.select(listOf("a", "b"), {}, { starts++; error("JNI") }, { false }, {})
            fail("Startup failure must propagate")
        } catch (error: IllegalStateException) { assertEquals("JNI", error.message) }
        assertEquals(1, starts)
    }
}
