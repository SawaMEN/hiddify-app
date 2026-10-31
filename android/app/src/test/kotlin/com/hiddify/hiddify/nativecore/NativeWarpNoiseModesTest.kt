package com.hiddify.hiddify.nativecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeWarpNoiseModesTest {
    @Test fun upstreamPresetOrderAndUniqueness() {
        assertEquals(listOf("m4", "random", "quic", "quic-mimic", "dns", "dns-cloudflare", "sip", "stun"),
            NativeWarpNoiseModes.presets)
        assertEquals(NativeWarpNoiseModes.presets.size, NativeWarpNoiseModes.presets.distinct().size)
    }

    @Test fun legacyCustomChoiceSurvivesEditorOpenAndSave() {
        val value = "my-custom-mode"
        val choices = NativeWarpNoiseModes.choicesFor(value)
        assertEquals(value, choices.last())
        assertEquals(1, choices.count { it == value })
        assertTrue(choices.contains("m4"))
        assertFalse(NativeWarpNoiseModes.presets.contains(value))
    }

    @Test fun knownAndEmptyValuesDoNotCreateDuplicateChoices() {
        assertEquals(NativeWarpNoiseModes.presets, NativeWarpNoiseModes.choicesFor("quic"))
        assertEquals(NativeWarpNoiseModes.presets, NativeWarpNoiseModes.choicesFor(""))
        assertEquals(NativeWarpNoiseModes.presets, NativeWarpNoiseModes.choicesFor("  m4  "))
    }
}
