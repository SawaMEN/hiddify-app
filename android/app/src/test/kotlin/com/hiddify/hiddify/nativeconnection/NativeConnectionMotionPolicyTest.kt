package com.hiddify.hiddify.nativeconnection

import com.hiddify.hiddify.constant.Status
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeConnectionMotionPolicyTest {
    private fun celebrate(
        previous: Status?,
        current: Status = Status.Started,
        motionEnabled: Boolean = true,
        recovering: Boolean = false,
        reconnecting: Boolean = false,
        failed: Boolean = false,
        requiresReconnect: Boolean = false,
    ) = NativeConnectionMotionPolicy.shouldCelebrate(
        previous, current, motionEnabled, recovering, reconnecting, failed, requiresReconnect
    )

    @Test fun onlyRealConnectingToConnectedTriggersOneShotRipple() {
        assertTrue(celebrate(Status.Starting))
        assertFalse(celebrate(Status.Started))
        assertFalse(celebrate(Status.Stopped))
        assertFalse(celebrate(null))
        assertFalse(celebrate(Status.Stopping))
        assertFalse(celebrate(Status.Starting, current = Status.Stopped))
    }

    @Test fun loadingAnAlreadyConnectedVPNNeverCelebrates() {
        // A new Compose screen starts with its first observed state, not a synthetic transition.
        assertFalse(celebrate(Status.Started))
        assertFalse(celebrate(Status.Started, recovering = true))
    }

    @Test fun doNotCelebrateFailedRecoveryPendingChangesOrReducedMotion() {
        assertFalse(celebrate(Status.Starting, motionEnabled = false))
        assertFalse(celebrate(Status.Starting, recovering = true))
        assertFalse(celebrate(Status.Starting, reconnecting = true))
        assertFalse(celebrate(Status.Starting, failed = true))
        assertFalse(celebrate(Status.Starting, requiresReconnect = true))
    }

    @Test fun progressMatchesOnlyLifecycleOrRecoveryStates() {
        assertTrue(NativeConnectionMotionPolicy.showProgress(Status.Starting, false, false))
        assertTrue(NativeConnectionMotionPolicy.showProgress(Status.Stopping, false, false))
        assertTrue(NativeConnectionMotionPolicy.showProgress(Status.Started, true, false))
        assertTrue(NativeConnectionMotionPolicy.showProgress(Status.Stopped, false, true))
        assertFalse(NativeConnectionMotionPolicy.showProgress(Status.Started, false, false))
        assertFalse(NativeConnectionMotionPolicy.showProgress(Status.Stopped, false, false))
    }
}
