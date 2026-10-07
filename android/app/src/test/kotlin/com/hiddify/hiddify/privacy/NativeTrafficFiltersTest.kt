package com.hiddify.hiddify.privacy

import org.junit.Assert.*
import org.junit.Test

class NativeTrafficFiltersTest {
    @Test fun absentAndMalformedPreferencesRemainDisabled() {
        assertTrue(NativeTrafficFilters.fromPreferences(emptyMap<String, Any>()).enabled.isEmpty())
        val values = mapOf("flutter.privacy-anonymization-block-quic" to "true",
            "flutter.privacy-anonymization-block-stun" to 1,
            "flutter.privacy-anonymization-block-plain-http" to null)
        assertTrue(NativeTrafficFilters.fromPreferences(values).enabled.isEmpty())
    }

    @Test fun readsOnlyExistingFlutterBooleanKeys() {
        val filters = NativeTrafficFilters.fromPreferences(mapOf(
            "flutter.privacy-anonymization-block-quic" to true,
            "privacy-anonymization-block-stun" to true,
            "flutter.adaptive_network" to true,
            "flutter.privacy-anonymization-isolate-lan" to false))
        assertEquals(setOf(NativeTrafficFilter.QUIC), filters.enabled)
    }

    @Test fun individualTogglePreservesOtherFilters() {
        val initial = NativeTrafficFilters(setOf(NativeTrafficFilter.LAN))
        val changed = initial.withFilter(NativeTrafficFilter.QUIC, true).withFilter(NativeTrafficFilter.LAN, false)
        assertEquals(setOf(NativeTrafficFilter.QUIC), changed.enabled)
        assertEquals(setOf(NativeTrafficFilter.LAN), initial.enabled)
        assertEquals(changed, changed.withFilter(NativeTrafficFilter.QUIC, true))
    }

    @Test fun bulkValuesRoundTripAndDoNotTouchOtherPolicies() {
        for (enabled in listOf(true, false)) {
            val filters = NativeTrafficFilters.all(enabled)
            val values = filters.preferenceValues()
            assertEquals(setOf("flutter.privacy-anonymization-block-quic", "flutter.privacy-anonymization-block-stun",
                "flutter.privacy-anonymization-block-plain-http", "flutter.privacy-anonymization-isolate-lan"), values.keys)
            assertEquals(setOf(enabled), values.values.toSet())
            assertEquals(filters, NativeTrafficFilters.fromPreferences(values))
        }
    }
}
