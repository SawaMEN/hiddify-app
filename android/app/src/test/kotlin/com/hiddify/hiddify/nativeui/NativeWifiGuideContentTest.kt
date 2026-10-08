package com.hiddify.hiddify.nativeui

import org.junit.Assert.*
import org.junit.Test

class NativeWifiGuideContentTest {
    @Test fun everyRecipientHasTheFullGuideInBothLanguages() {
        for (ru in listOf(false, true)) for (platform in 0..4) {
            val guide = nativeWifiGuide(platform, ru, "192.168.42.1", 23456, false, "Phone network")
            assertEquals(6, guide.size)
            assertEquals(3, guide[0].steps.size)
            assertEquals(3, guide[1].steps.size)
            assertEquals(4, guide[2].steps.size)
            assertTrue(guide[0].steps.first().contains("Phone network"))
            assertTrue(guide[2].fields.values.any { it.contains("23456") })
            assertFalse(guide.flatMap { it.steps }.any { it.contains("\$host") || it.contains("\$ssid") })
        }
    }
    @Test fun rootGuideUsesEachOperatingSystemsOwnDisableProxyPath() {
        val platforms = listOf("Android:", "Windows:", "Linux:", "macOS:", "iOS:")
        for (platform in 0..4) {
            val guide = nativeWifiGuide(platform, false, "192.168.42.1", 12334, true, "Phone")
            assertEquals(5, guide.size)
            assertTrue(guide[1].steps[2].startsWith(platforms[platform]))
            assertEquals("Not required", guide[1].fields["Proxy credentials"])
            assertEquals(2, guide[2].steps.size)
        }
    }
}
