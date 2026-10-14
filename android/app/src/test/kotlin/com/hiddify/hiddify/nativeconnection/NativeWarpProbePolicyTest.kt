package com.hiddify.hiddify.nativeconnection

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class NativeWarpProbePolicyTest {
    @Test fun avoidsDnsAndAllowsHandshakeRetransmissionWithinStartupBudget() {
        assertTrue(NativeWarpProbePolicy.targets.first().seconds > 5)
        assertTrue(NativeWarpProbePolicy.targets.all { URI(it.url).host == "1.1.1.1" })
        assertEquals("https", URI(NativeWarpProbePolicy.targets.first().url).scheme)
        assertEquals(NativeWarpProbePolicy.BUDGET_MS, NativeWarpProbePolicy.targets.sumOf { it.seconds } * 1000)
        assertTrue(6 * NativeWarpProbePolicy.BUDGET_MS < 90_000)
    }
}
