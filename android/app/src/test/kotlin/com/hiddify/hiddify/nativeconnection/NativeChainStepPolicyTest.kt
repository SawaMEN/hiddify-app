package com.hiddify.hiddify.nativeconnection

import com.hiddify.hiddify.nativecore.*
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeChainStepPolicyTest {
    private fun node(status: NativeOutboundStatus, delay: Int = 0) = NativeOutbound("x", "x", "warp",
        false, true, delay, "", 0, 0, 0, null, status = status)
    private val ready = node(NativeOutboundStatus.READY, 100)
    private val connecting = node(NativeOutboundStatus.CONNECTING)

    @Test fun missingTelemetryNeverTimesOutIntoAFabricatedFailure() {
        assertEquals(NativeChainStepState.UNKNOWN, NativeChainStepPolicy.hop(null, ready, "extra_security", Long.MAX_VALUE))
        assertEquals(NativeChainStepState.UNKNOWN, NativeChainStepPolicy.hop(ready, ready, "off", 0))
    }
    @Test fun extraSecurityWaitsForTheServerWhileUnblockerDialsFirst() {
        assertEquals(NativeChainStepState.WAITING, NativeChainStepPolicy.hop(ready, connecting, "extra_security", 0))
        assertEquals(NativeChainStepState.READY, NativeChainStepPolicy.hop(ready, connecting, "unblocker", 0))
    }
    @Test fun observedPendingHopHasSlowAndFailureBoundariesAndCanRecover() {
        assertEquals(NativeChainStepState.CONNECTING, NativeChainStepPolicy.hop(connecting, ready, "extra_security", 14999))
        assertEquals(NativeChainStepState.SLOW, NativeChainStepPolicy.hop(connecting, ready, "extra_security", 15000))
        assertEquals(NativeChainStepState.FAILED, NativeChainStepPolicy.hop(connecting, ready, "extra_security", 60000))
        assertEquals(NativeChainStepState.READY, NativeChainStepPolicy.hop(ready, ready, "extra_security", 70000))
        assertEquals(NativeChainStepState.FAILED, NativeChainStepPolicy.hop(node(NativeOutboundStatus.FAILED, 100), ready, "unblocker", 0))
    }
}
