package com.hiddify.hiddify.nativecore

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeLanSharingLinkTest {
    @Test fun emptyPasswordOmitsAuthenticationAndDisabledPortUsesTheDartFallback() {
        assertEquals("socks://192.168.1.2:12334", NativeLanSharingLink.create("192.168.1.2", 0, ""))
        assertEquals("socks://192.168.1.2:1080", NativeLanSharingLink.create("192.168.1.2", 1080, ""))
    }

    @Test fun whitespaceAndReservedPasswordCharactersArePreserved() {
        val link = NativeLanSharingLink.create("192.168.1.2", 12334, " a:@/+#% ")
        assertEquals("socks://hiddify:%20a%3A%40%2F%2B%23%25%20@192.168.1.2:12334", link)
        assertEquals("hiddify: a:@/+#% ", URI(link).userInfo)
        assertFalse(link.contains("%2520"))
    }

    @Test fun ipv6AndUnicodeCredentialsRoundTripWithoutChangingTheAuthority() {
        val link = NativeLanSharingLink.create("[2001:db8::1]", 1080, "пароль🔐")
        val uri = URI(link)
        assertEquals("[2001:db8::1]", uri.host)
        assertEquals(1080, uri.port)
        assertEquals("hiddify:пароль🔐", uri.userInfo)
        assertEquals(link, NativeLanSharingLink.create("2001:db8::1", 1080, "пароль🔐"))
    }

    @Test fun invalidAddressesCannotInjectCredentialsPathsOrExtraAuthorities() {
        listOf("", "192.168.1.2@evil", "192.168.1.2/path", "192.168.1.2?query",
            "192.168.1.2#fragment", "192.168.1.2\\evil", "[2001:db8::1", "not:ipv6").forEach { host ->
            assertThrows(Exception::class.java) { NativeLanSharingLink.create(host, 1080, "secret") }
        }
        assertThrows(IllegalArgumentException::class.java) { NativeLanSharingLink.create("192.168.1.2", 65536, "") }
    }
}
