package com.hiddify.hiddify.nativeprofile

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class NativeSubscriptionContentTest {
    private fun encode(text: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())
    private fun expand(text: String, response: String) = NativeSubscriptionContent.expand(text,
        { it.startsWith("https://") }, { response })

    @Test fun expandsEncodedParentAndChildWithoutLeavingBase64AmongLinks() {
        val source = encode("vless://one\nhttps://subscription.example/child")
        assertEquals("vless://one\ntrojan://two", expand(source, encode("trojan://two")))
    }

    @Test fun urlSafeAndWrappedEnvelopesDecodeLikeTheCore() {
        val links = "vless://one#Москва\ntrojan://two"
        assertEquals(links, NativeSubscriptionContent.decode("\uFEFF" + encode(links).chunked(8).joinToString("\n")))
        assertEquals(links, NativeSubscriptionContent.decode(links))
    }

    @Test fun sizeLimitCountsTheExpandedDocumentRatherThanRemovedUrls() {
        val response = "vless://" + "a".repeat(NativeProfileTransfer.MAX_CONFIG_BYTES - 8)
        assertEquals(response, expand("https://subscription.example/child", response))
        assertThrows(IllegalArgumentException::class.java) {
            expand("https://subscription.example/child\nx", response)
        }
    }

    @Test fun invalidUtf8IsRejectedBeforeItCanCorruptSavedCredentials() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeProfileTransfer.readText(byteArrayOf(0xC3.toByte(), 0x28).inputStream())
        }
        val invalidEnvelope = Base64.getEncoder().encodeToString(byteArrayOf(0xC3.toByte(), 0x28))
        assertEquals(invalidEnvelope, NativeSubscriptionContent.decode(invalidEnvelope))
    }

    @Test fun excessiveNestedUrlsAreRejectedBeforeAnyDownload() {
        var downloads = 0
        assertThrows(IllegalArgumentException::class.java) {
            NativeSubscriptionContent.expand(List(129) { "https://example.org/$it" }.joinToString("\n"),
                { it.startsWith("https://") }, { downloads++; "vless://one" })
        }
        assertEquals(0, downloads)
    }
    @Test fun crlfAndCommentsSurviveNestedSubscriptionExpansion() {
        val parent = "# profile-title: My servers\r\nvless://one\r\nhttps://example.org/nested\r\n"
        val child = encode("trojan://child\nss://second")
        assertEquals("# profile-title: My servers\r\nvless://one\r\ntrojan://child\nss://second\n",
            NativeSubscriptionContent.expand(parent, { it.startsWith("https://") }, { child }))
    }

    @Test fun cancellationStopsBeforeTheNextNestedRequest() {
        val downloads = mutableListOf<String>()
        var checks = 0
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            NativeSubscriptionContent.expand("https://example.org/first\nhttps://example.org/second",
                { it.startsWith("https://") },
                { url -> downloads += url; "vless://child" },
                ensureActive = {
                    if (++checks >= 2) throw kotlinx.coroutines.CancellationException("cancelled")
                })
        }
        assertEquals(listOf("https://example.org/first"), downloads)
    }

    @Test fun failedNestedDownloadNeverReturnsPartiallyExpandedContent() {
        val downloads = mutableListOf<String>()
        assertThrows(IllegalStateException::class.java) {
            NativeSubscriptionContent.expand("https://example.org/first\nhttps://example.org/second",
                { it.startsWith("https://") }, { url ->
                    downloads += url
                    if (url.endsWith("second")) throw IllegalStateException("HTTP 502")
                    "vless://child"
                })
        }
        assertEquals(2, downloads.size)
    }

}
