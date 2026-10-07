package com.hiddify.hiddify.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotSharingTest {
    @Test fun selectsWirelessAddressDuringCellularHandover() {
        val addresses = linkedMapOf(
            "rmnet_data0/10.1.2.3" to ("rmnet_data0" to "10.1.2.3"),
            "swlan0/192.168.43.1" to ("swlan0" to "192.168.43.1"),
        )
        assertEquals("swlan0" to "192.168.43.1", HotspotInterfaceSelection.find(emptySet(), addresses))
        assertNull(HotspotInterfaceSelection.find(addresses.keys, addresses))
        assertNull(HotspotInterfaceSelection.find(emptySet(), addresses +
            ("ap0/192.168.42.1" to ("ap0" to "192.168.42.1"))))
    }

    @Test fun wifiQrEscapesCredentialDelimiters() {
        assertEquals("WIFI:T:WPA;S:Test;P:password;;", HotspotWifiQr.encode("Test", "password"))
        assertEquals("WIFI:T:WPA;S:A\\;B;P:a\\:b\\,c;;", HotspotWifiQr.encode("A;B", "a:b,c"))
    }

    @Test fun forwardingIsGuardedBeforeItIsEnabled() {
        val script = HotspotRootRules.install("ap0", 2022, HotspotRootRules.chain(123))
        assertTrue(script.indexOf("-I FORWARD") < script.indexOf("echo 1"))
        assertTrue("-i ap0 -o hc2022 -j ACCEPT" in script)
        assertTrue("-i ap0 -j REJECT" in script)
        assertTrue(runCatching { HotspotRootRules.install("ap0;id", 2022, HotspotRootRules.chain(123)) }.isFailure)
    }
}
