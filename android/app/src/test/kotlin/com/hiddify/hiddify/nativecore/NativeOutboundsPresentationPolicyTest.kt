package com.hiddify.hiddify.nativecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeOutboundsPresentationPolicyTest {
    private val old = NativeOutboundGroup("select", "selector", "server-a", true, emptyList())
    private val fresh = NativeOutboundGroup("select", "selector", "server-b", true, emptyList())

    @Test fun resubscribeKeepsLastValidServerList() {
        val loaded = NativeOutboundsPresentationPolicy.received(
            NativeOutboundsPresentationPolicy.initial(true),
            NativeOutboundUpdate(group = old, snapshot = true),
        )
        val resubscribing = NativeOutboundsPresentationPolicy.resubscribing(loaded)
        val onNewStream = NativeOutboundsPresentationPolicy.received(resubscribing, NativeOutboundUpdate())
        assertEquals(old, onNewStream.group)
        assertFalse(onNewStream.loading)
        assertNull(onNewStream.error)
    }

    @Test fun transientErrorKeepsListThenNewSnapshotRecovers() {
        val loaded = NativeOutboundsPresentationPolicy.received(
            NativeOutboundsPresentationPolicy.initial(true),
            NativeOutboundUpdate(group = old, snapshot = true),
        )
        val failed = NativeOutboundsPresentationPolicy.received(
            loaded, NativeOutboundUpdate(error = "gRPC transport failure"))
        assertEquals(old, failed.group)
        assertEquals("gRPC transport failure", failed.error)
        val recovered = NativeOutboundsPresentationPolicy.received(
            failed, NativeOutboundUpdate(group = fresh, snapshot = true))
        assertEquals(fresh, recovered.group)
        assertNull(recovered.error)
    }

    @Test fun explicitEmptyResponseClearsOldGroupButReconnectMarkerDoesNot() {
        val loaded = NativeOutboundsPresentationPolicy.received(
            NativeOutboundsPresentationPolicy.initial(true),
            NativeOutboundUpdate(group = old, snapshot = true))
        assertEquals(old, NativeOutboundsPresentationPolicy.received(loaded, NativeOutboundUpdate()).group)
        val empty = NativeOutboundsPresentationPolicy.received(
            loaded, NativeOutboundUpdate(snapshot = true))
        assertNull(empty.group)
        assertFalse(empty.loading)
    }

    @Test fun actualVpnDisconnectClearsCachedOutbounds() {
        val state = NativeOutboundsPresentationPolicy.initial(false)
        assertNull(state.group)
        assertFalse(state.loading)
        assertTrue(NativeOutboundsPresentationPolicy.initial(true).loading)
    }
}
