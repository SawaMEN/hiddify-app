package com.hiddify.hiddify.nativeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class NativeSubscriptionPopupPolicyTest {
    @Test fun latestMessageSurvivesOlderAutoDismiss() {
        val latest = NativeSubscriptionBanner(12L, "Updated", NativeSubscriptionBanner.Kind.SUCCESS)
        assertSame(latest, NativeSubscriptionPopupPolicy.dismiss(latest, 11L))
        assertNull(NativeSubscriptionPopupPolicy.dismiss(latest, 12L))
    }

    @Test fun clearedPopupCannotReappearFromDelayedCallback() {
        assertNull(NativeSubscriptionPopupPolicy.dismiss(null, 13L))
    }

    @Test fun allKindsUseBoundedDisplayDurations() {
        assertEquals(3400L, NativeSubscriptionPopupPolicy.durationMillis(NativeSubscriptionBanner.Kind.SUCCESS))
        assertEquals(3400L, NativeSubscriptionPopupPolicy.durationMillis(NativeSubscriptionBanner.Kind.INFO))
        assertEquals(5200L, NativeSubscriptionPopupPolicy.durationMillis(NativeSubscriptionBanner.Kind.ERROR))
    }
}
