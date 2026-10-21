package com.hiddify.hiddify.nativecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeControlPortPolicyTest {
    @Test fun replacesLegacySharedPortEvenIfFree() {
        assertEquals(32000, NativeControlPortPolicy.select(
            current = 17079, frontPort = 17078,
            isAvailable = { true }, allocateEphemeral = { 32000 },
        ))
    }

    @Test fun keepsStableFreePerInstallPort() {
        assertEquals(32000, NativeControlPortPolicy.select(
            current = 32000, frontPort = 17078,
            isAvailable = { it == 32000 }, allocateEphemeral = { error("unexpected allocation") },
        ))
    }

    @Test fun changesOccupiedPortAndRejectsReservedCandidates() {
        val ports = mutableListOf(17079, 17078, 32001)
        assertEquals(32001, NativeControlPortPolicy.select(
            current = 32000, frontPort = 17078,
            isAvailable = { it == 32001 },
            allocateEphemeral = { ports.removeAt(0) },
        ))
    }

    @Test fun failsClearlyIfNoValidPortCanBeAllocated() {
        assertThrows(IllegalStateException::class.java) {
            NativeControlPortPolicy.select(
                current = 17079, frontPort = 17078,
                isAvailable = { false }, allocateEphemeral = { 17079 },
            )
        }
    }
}
