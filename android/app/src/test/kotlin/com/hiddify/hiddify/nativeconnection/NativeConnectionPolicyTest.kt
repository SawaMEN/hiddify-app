package com.hiddify.hiddify.nativeconnection

import org.junit.Assert.*
import org.junit.Test

class NativeConnectionOptionsTest {
    @Test fun autoReconnectMatchesLegacyDefaultAndCanBeDisabled() {
        assertTrue(NativeConnectionOptions.fromPreferences(emptyMap<String, Any>()).autoReconnect)
        val disabled = NativeConnectionOptions(autoReconnect = false)
        assertEquals(disabled, NativeConnectionOptions.fromPreferences(disabled.preferences()))
        assertFalse(disabled.recoveryEnabled)
        assertTrue(disabled.copy(adaptiveNetwork = true).recoveryEnabled)
        assertFalse(disabled.corePolicy().containsKey("auto-reconnect"))
    }

    @Test fun absentAndMalformedValuesAreOptOut() {
        assertEquals(NativeConnectionOptions(), NativeConnectionOptions.fromPreferences(emptyMap<String, Any>()))
        assertEquals(NativeConnectionOptions(), NativeConnectionOptions.fromPreferences(mapOf(
            "flutter.privacy-modern-protocols-only" to "true", "flutter.privacy-modern-allow-udp" to 1,
            "flutter.adaptive_network" to null)))
    }

    @Test fun preferencesRoundTripWithoutOtherTrafficKeys() {
        for (masked in listOf(false, true)) for (udp in listOf(false, true)) for (adaptive in listOf(false, true)) {
            val options = NativeConnectionOptions(masked, udp, adaptive)
            assertEquals(options, NativeConnectionOptions.fromPreferences(options.preferences()))
            assertEquals(setOf("flutter.privacy-modern-protocols-only", "flutter.privacy-modern-allow-udp",
                "flutter.adaptive_network", "flutter.auto_reconnect"), options.preferences().keys)
            assertEquals(mapOf("privacy-modern-protocols-only" to masked, "privacy-modern-allow-udp" to udp,
                "adaptive-network" to adaptive), options.corePolicy())
        }
    }

    @Test fun disablingRestrictionPreservesUdpChoiceAndAdaptivePolicy() {
        val value = NativeConnectionOptions(true, true, true).copy(maskedProtocolsOnly = false)
        assertEquals(NativeConnectionOptions(false, true, true), value)
    }

    @Test fun unrelatedAndUnprefixedKeysDoNotEnableFeatures() {
        assertEquals(NativeConnectionOptions(), NativeConnectionOptions.fromPreferences(mapOf(
            "privacy-modern-protocols-only" to true, "adaptive-network" to true,
            "flutter.privacy-anonymization-block-quic" to true)))
    }
}

class NativeHealthProbePolicyTest {
    @Test fun normalModeUsesFixedTiming() {
        val policy = NativeHealthProbePolicy(false)
        repeat(10) { policy.record(it % 2 == 0, 20_000) }
        assertEquals(12L, policy.timeoutSeconds)
        assertEquals(30L, policy.intervalSeconds)
    }

    @Test fun adaptiveWarmupRequiresThreeSuccessfulResponses() {
        val policy = NativeHealthProbePolicy(true)
        assertEquals(30L, policy.timeoutSeconds)
        assertEquals(60L, policy.intervalSeconds)
        repeat(2) { policy.record(true, 100); assertEquals(30L, policy.timeoutSeconds) }
        policy.record(true, 100)
        assertEquals(12L, policy.timeoutSeconds)
    }

    @Test fun timeoutAdaptsToMeasuredLatencyAndRemainsBounded() {
        val policy = NativeHealthProbePolicy(true)
        repeat(3) { policy.record(true, 2_000) }
        assertEquals(16L, policy.timeoutSeconds)
        repeat(10) { policy.record(true, 30_000) }
        assertEquals(30L, policy.timeoutSeconds)
        repeat(20) { policy.record(true, 1) }
        assertEquals(12L, policy.timeoutSeconds)
    }

    @Test fun failureRestoresLongTimeoutUntilThreeNewSuccesses() {
        val policy = NativeHealthProbePolicy(true)
        repeat(3) { policy.record(true, 100) }
        repeat(2) { policy.record(false, 30_000) }
        assertEquals(2, policy.failures)
        assertEquals(30L, policy.timeoutSeconds)
        assertEquals(30L, policy.intervalSeconds)
        repeat(2) { policy.record(true, 100); assertEquals(30L, policy.timeoutSeconds) }
        assertEquals(0, policy.failures)
        assertEquals(60L, policy.intervalSeconds)
        policy.record(true, 100)
        assertEquals(12L, policy.timeoutSeconds)
    }

    @Test fun separateSessionsDoNotInheritFailureHistory() {
        val previous = NativeHealthProbePolicy(true)
        previous.record(false, 30_000)
        val fresh = NativeHealthProbePolicy(true)
        assertEquals(0, fresh.failures)
        assertEquals(30L, fresh.timeoutSeconds)
        assertEquals(60L, fresh.intervalSeconds)
    }

    @Test fun invalidElapsedValuesCannotEscapeDeadlineLimits() {
        val policy = NativeHealthProbePolicy(true)
        repeat(3) { policy.record(true, -1) }
        assertEquals(12L, policy.timeoutSeconds)
        repeat(3) { policy.record(true, Long.MAX_VALUE) }
        assertEquals(30L, policy.timeoutSeconds)
    }
}
