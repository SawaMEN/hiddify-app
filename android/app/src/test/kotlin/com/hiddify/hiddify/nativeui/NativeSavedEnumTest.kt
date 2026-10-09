package com.hiddify.hiddify.nativeui

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeSavedEnumTest {
    private enum class Destination { HOME, SETTINGS, PRIVACY }

    @Test fun restoresKnownValue() {
        assertEquals(Destination.SETTINGS, restoredEnumOrDefault("SETTINGS", Destination.HOME))
    }

    @Test fun fallsBackForRemovedDestination() {
        assertEquals(Destination.HOME, restoredEnumOrDefault("LEGACY", Destination.HOME))
    }

    @Test fun fallsBackForMalformedOrEmptyValues() {
        assertEquals(Destination.PRIVACY, restoredEnumOrDefault("", Destination.PRIVACY))
        assertEquals(Destination.PRIVACY, restoredEnumOrDefault("settings", Destination.PRIVACY))
    }
}
