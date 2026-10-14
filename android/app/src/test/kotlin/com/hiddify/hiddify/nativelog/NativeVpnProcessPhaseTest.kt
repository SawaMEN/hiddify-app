package com.hiddify.hiddify.nativelog

import org.junit.Assert.*
import org.junit.Test

class NativeVpnProcessPhaseTest {
    @Test fun everyConnectionStageFitsAndroids128ByteSummary() {
        NativeVpnProcessPhase.entries.forEach { phase ->
            assertTrue(phase.summary().size in 1..128)
            assertEquals(phase, NativeVpnProcessPhase.fromSummary(phase.summary()))
        }
    }

    @Test fun rejectsUnknownOrPrivateProcessSummaries() {
        assertNull(NativeVpnProcessPhase.fromSummary(null))
        assertNull(NativeVpnProcessPhase.fromSummary("vpn:unknown https://private/token".toByteArray()))
        assertNull(NativeVpnProcessPhase.fromSummary("connected".toByteArray()))
        assertNull(NativeVpnProcessPhase.fromSummary(ByteArray(129)))
        assertNull(NativeVpnProcessPhase.fromSummary(byteArrayOf(0xff.toByte(), 0)))
    }
}
