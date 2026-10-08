package com.hiddify.hiddify.nativecore

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class NativeServiceModeOptionsTest {
    @Test fun freshAndroidVpnConfigCreatesTunInbound() {
        val root = JsonParser.parseString(NativeServiceModeOptions.apply("", true)).asJsonObject
        assertTrue(root.get("enable-tun").asBoolean)
    }

    @Test fun vpnModeOverridesOldDisabledTunSettingWithoutLosingOtherSettings() {
        val root = JsonParser.parseString(NativeServiceModeOptions.apply(
            """{"enable-tun":false,"mixed-port":13337,"tls-tricks":{"enable-fragment":true}}""", true,
        )).asJsonObject
        assertTrue(root.get("enable-tun").asBoolean)
        assertEquals(13337, root.get("mixed-port").asInt)
        assertTrue(root.getAsJsonObject("tls-tricks").get("enable-fragment").asBoolean)
    }

    @Test fun proxyModeNeverRequestsAndroidTun() {
        val root = JsonParser.parseString(NativeServiceModeOptions.apply(
            """{"enable-tun":true,"enable-mixed-port":true}""", false,
        )).asJsonObject
        assertFalse(root.get("enable-tun").asBoolean)
        assertTrue(root.get("enable-mixed-port").asBoolean)
    }

    @Test fun inputIsNotMutatedAndInvalidSettingsAreRejected() {
        val original = """{"enable-tun":false,"region":"ru"}"""
        NativeServiceModeOptions.apply(original, true)
        assertEquals("""{"enable-tun":false,"region":"ru"}""", original)
        assertThrows(IllegalStateException::class.java) {
            NativeServiceModeOptions.apply("[]", true)
        }
    }
}
