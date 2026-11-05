package com.hiddify.hiddify.nativeprofile

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class NativeTvTransferPolicyTest {
    private fun ip(value: String) = InetAddress.getByName(value)

    @Test fun rejectsPublicHostsAndPeersOutsideTheSameWifi() {
        assertTrue(NativeTvTransferPolicy.isPrivateIpv4(ip("192.168.1.20")))
        assertTrue(NativeTvTransferPolicy.isPrivateIpv4(ip("172.20.1.2")))
        assertFalse(NativeTvTransferPolicy.isPrivateIpv4(ip("127.0.0.1")))
        assertFalse(NativeTvTransferPolicy.isPrivateIpv4(ip("8.8.8.8")))
        assertFalse(NativeTvTransferPolicy.isPrivateIpv4(ip("100.64.0.1")))
        assertTrue(NativeTvTransferPolicy.sameSubnet(ip("192.168.1.20"), ip("192.168.1.5"), 24))
        assertFalse(NativeTvTransferPolicy.sameSubnet(ip("192.168.1.20"), ip("192.168.2.5"), 24))
        assertTrue(NativeTvTransferPolicy.sameSubnet(ip("172.20.0.1"), ip("172.20.15.5"), 20))
        assertFalse(NativeTvTransferPolicy.sameSubnet(ip("10.2.1.1"), ip("10.3.1.1"), 16))
    }

    @Test fun dnsRebindingHostHeaderMustBeTheNumericLocalIpAndPort() {
        assertTrue(NativeTvTransferPolicy.matchesHost("192.168.1.20:55555", "192.168.1.20", 55555))
        for (host in listOf("example.com:55555", "192.168.1.20", "127.0.0.1:55555",
                "192.168.1.20:12345", null)) {
            assertFalse(NativeTvTransferPolicy.matchesHost(host, "192.168.1.20", 55555))
        }
    }

    @Test fun acceptsOnlyWellFormedSubscriptionLinks() {
        assertEquals("https://example.com/sub?token=a%20b",
            NativeTvTransferPolicy.validSubscriptionUrl(" https://example.com/sub?token=a%20b "))
        for (value in listOf("", "file:///etc/passwd", "ftp://example.com", "https://",
                "https://example.com/a b", "https://user:pass@example.com",
                "https://example.com/path#fragment", "http://example.com:0",
                "https://example.com:65536")) {
            assertNull(value, NativeTvTransferPolicy.validSubscriptionUrl(value))
        }
    }
}
