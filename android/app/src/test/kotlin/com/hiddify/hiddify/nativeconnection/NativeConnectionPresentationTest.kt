package com.hiddify.hiddify.nativeconnection

import com.hiddify.hiddify.constant.Status
import org.junit.Assert.*
import org.junit.Test

class NativeConnectionPresentationTest {
    @Test fun onlyAnEstablishedConnectionWithoutFailuresOrPendingSettingsIsGreen() {
        for (status in Status.entries) for (recovering in listOf(false, true))
            for (reconnecting in listOf(false, true)) for (failed in listOf(false, true))
                for (pending in listOf(false, true)) {
                    assertEquals(status == Status.Started && !recovering && !reconnecting && !failed && !pending,
                        NativeConnectionPresentation.tone(status, recovering, reconnecting, failed, pending) == NativeConnectionTone.CONNECTED)
                }
    }

    @Test fun failureOverridesTheLastStartedCallbackAndPendingSettings() {
        for (status in listOf(Status.Started, Status.Stopped)) {
            assertEquals(NativeConnectionTone.FAILED, NativeConnectionPresentation.tone(status, false, false, true, true))
        }
        assertEquals(NativeConnectionTone.PENDING, NativeConnectionPresentation.tone(Status.Started, false, false, false, true))
        assertEquals(NativeConnectionTone.PENDING, NativeConnectionPresentation.tone(Status.Stopped, true, false, true, false))
        assertEquals(NativeConnectionTone.PENDING, NativeConnectionPresentation.tone(Status.Started, false, true, true, false))
    }

    @Test fun serviceDeathIsAnErrorButUserStopAndInitialBindingAreNot() {
        assertTrue(NativeConnectionPresentation.unexpectedStop(Status.Started, Status.Stopped, true))
        assertFalse(NativeConnectionPresentation.unexpectedStop(Status.Started, Status.Stopped, false))
        assertFalse(NativeConnectionPresentation.unexpectedStop(Status.Stopping, Status.Stopped, true))
        assertFalse(NativeConnectionPresentation.unexpectedStop(Status.Stopped, Status.Stopped, true))
        assertFalse(NativeConnectionPresentation.unexpectedStop(Status.Starting, Status.Stopped, true))
    }
}
