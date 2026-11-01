package com.hiddify.hiddify.nativeconnection

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeConnectionPathPolicyTest {
    @Test fun noSyntheticFailureWhenDisconnectedOrUntested() {
        assertEquals(NativeConnectionWarning.NONE,
            NativeConnectionPathPolicy.warning(false, 65001, NativeInternetHealth.UNAVAILABLE))
        assertEquals(NativeConnectionWarning.NONE,
            NativeConnectionPathPolicy.warning(true, null, NativeInternetHealth.UNCHECKED))
        assertEquals(NativeConnectionWarning.NONE,
            NativeConnectionPathPolicy.warning(true, 0, NativeInternetHealth.CHECKING))
    }

    @Test fun realTimeoutTakesPriorityOverFailedProbe() {
        assertEquals(NativeConnectionWarning.SELECTED_SERVER_TIMED_OUT,
            NativeConnectionPathPolicy.warning(true, 65001, NativeInternetHealth.PROBE_FAILED))
        assertEquals(NativeConnectionWarning.NONE,
            NativeConnectionPathPolicy.warning(true, 65000, NativeInternetHealth.AVAILABLE))
    }

    @Test fun failedProbeIsShownWithoutMisrepresentingUnverifiedChain() {
        assertEquals(NativeConnectionWarning.INTERNET_PROBE_FAILED,
            NativeConnectionPathPolicy.warning(true, 120, NativeInternetHealth.UNAVAILABLE))
        assertEquals(NativeConnectionWarning.INTERNET_PROBE_INCONCLUSIVE,
            NativeConnectionPathPolicy.warning(true, null, NativeInternetHealth.PROBE_FAILED))
        assertEquals(NativeConnectionWarning.NONE,
            NativeConnectionPathPolicy.warning(true, 120, NativeInternetHealth.AVAILABLE))
    }
}
