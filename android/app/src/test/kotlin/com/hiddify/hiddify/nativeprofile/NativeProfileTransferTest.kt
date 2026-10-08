package com.hiddify.hiddify.nativeprofile

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class NativeProfileTransferTest {
    @Test fun preservesEncodedTokensPortAndUnicodeName() {
        val url = "https://user:pass@vpn.example:2096/sub?token=a%2Fb%2Bc#old"
        val link = NativeProfileTransfer.subscriptionLink(url, "Мой VPN #1")
        assertTrue(link.startsWith(url.substringBefore('#') + "#"))
        assertEquals("Мой VPN #1", java.net.URI(link).fragment)
    }

    @Test fun preservesIpv6Authority() {
        val link = NativeProfileTransfer.subscriptionLink("https://[2001:db8::1]:2096/a?x=%20", "VPN")
        assertEquals("https://[2001:db8::1]:2096/a?x=%20#VPN", link)
    }

    @Test fun rejectsNonSubscriptionSchemes() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeProfileTransfer.subscriptionLink("file:///private/config", "VPN")
        }
    }

    @Test fun malformedSubscriptionUrlsUseValidationErrors() {
        listOf("https://", "https://example.com:0/sub", "https://example.com:65536/sub", "https://example.com/a b").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) {
                NativeProfileTransfer.subscriptionLink(url, "VPN")
            }
        }
    }

    @Test fun readsUtf8AndRemovesBom() {
        val input = ByteArrayInputStream("\uFEFFvless://token#Москва".toByteArray())
        assertEquals("vless://token#Москва", NativeProfileTransfer.readText(input))
    }

    @Test fun acceptsExactLimit() {
        val input = ByteArrayInputStream(ByteArray(NativeProfileTransfer.MAX_CONFIG_BYTES) { 65 })
        assertEquals(NativeProfileTransfer.MAX_CONFIG_BYTES, NativeProfileTransfer.readText(input).length)
    }

    @Test fun stopsReadingOversizedDocuments() {
        var read = 0
        val input = object : InputStream() {
            override fun read(): Int { read++; return 65 }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                buffer.fill(65, offset, offset + length)
                read += length
                return length
            }
        }
        assertThrows(IllegalArgumentException::class.java) { NativeProfileTransfer.readText(input) }
        assertTrue(read <= NativeProfileTransfer.MAX_CONFIG_BYTES + 8192)
    }
}
