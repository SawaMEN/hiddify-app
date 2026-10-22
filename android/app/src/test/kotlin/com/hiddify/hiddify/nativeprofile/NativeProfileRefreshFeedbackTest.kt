package com.hiddify.hiddify.nativeprofile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeProfileRefreshFeedbackTest {
    @Test fun profileResultCannotAppearOnAnotherProfile() {
        val updating = NativeProfileRefreshFeedback("profile-a", NativeProfileRefreshPhase.UPDATING)
        assertEquals(NativeProfileRefreshPhase.UPDATING,
            NativeProfileRefreshMotionPolicy.phaseFor("profile-a", updating))
        assertNull(NativeProfileRefreshMotionPolicy.phaseFor("profile-b", updating))
    }

    @Test fun bulkUpdateHasItsOwnState() {
        val result = NativeProfileRefreshFeedback(
            NativeProfileRefreshMotionPolicy.UPDATE_ALL_ID, NativeProfileRefreshPhase.SUCCESS)
        assertEquals(NativeProfileRefreshPhase.SUCCESS,
            NativeProfileRefreshMotionPolicy.phaseFor(NativeProfileRefreshMotionPolicy.UPDATE_ALL_ID, result))
        assertNull(NativeProfileRefreshMotionPolicy.phaseFor("profile-a", result))
        assertNull(NativeProfileRefreshMotionPolicy.phaseFor("profile-a", null))
    }

    @Test fun failureIsNotReportedAsSuccess() {
        val failure = NativeProfileRefreshFeedback("profile-a", NativeProfileRefreshPhase.FAILURE)
        assertEquals(NativeProfileRefreshPhase.FAILURE,
            NativeProfileRefreshMotionPolicy.phaseFor("profile-a", failure))
    }
}
