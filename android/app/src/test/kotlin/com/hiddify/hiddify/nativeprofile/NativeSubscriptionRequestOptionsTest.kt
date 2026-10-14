package com.hiddify.hiddify.nativeprofile

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class NativeSubscriptionRequestOptionsTest {
    @Test fun defaultsAndProfileIsolation() {
        val custom = NativeSubscriptionRequestOptions("Provider/1", "x-device-os: Android\nAuthorization: Bearer test")
        val first = custom.request("https://provider.example/sub")
        val other = NativeSubscriptionRequestOptions().request("https://other.example/sub")
        assertEquals("Provider/1", first.header("User-Agent"))
        assertEquals("Bearer test", first.header("Authorization"))
        assertEquals("VetrOFF-Android", other.header("User-Agent"))
        assertNull(other.header("Authorization"))
        assertNull(other.header("x-device-os"))
    }

    @Test fun invalidOrTransportHeadersAreRejected() {
        listOf("User-Agent: custom", "Host: other", "Connection: close", "X: first\nx: second", "Bad Name: value", "Missing separator", "X: bad\u0000value").forEach {
            assertThrows(IllegalArgumentException::class.java) { NativeSubscriptionRequestOptions(headers = it).validated() }
        }
        assertThrows(IllegalArgumentException::class.java) { NativeSubscriptionRequestOptions("bad\r\nX: injected").validated() }
    }

    @Test fun sameOriginRedirectRetainsProviderHeaders() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/next"))
            server.enqueue(MockResponse().setBody("profile"))
            val options = NativeSubscriptionRequestOptions("Provider/1", "x-hwid: local-test\nAuthorization: Bearer test")
            val request = options.request(server.url("/sub").toString())
            options.client(OkHttpClient(), request).newCall(request).execute().use { assertEquals("profile", it.body!!.string()) }
            repeat(2) {
                val observed = server.takeRequest(2, TimeUnit.SECONDS)!!
                assertEquals("Provider/1", observed.getHeader("User-Agent"))
                assertEquals("local-test", observed.getHeader("x-hwid"))
                assertEquals("Bearer test", observed.getHeader("Authorization"))
            }
        }
    }

    @Test fun otherOriginRedirectDoesNotLeakHeadersOrClientIdentity() {
        MockWebServer().use { first -> MockWebServer().use { other ->
            other.enqueue(MockResponse().setBody("profile"))
            first.enqueue(MockResponse().setResponseCode(302).addHeader("Location", other.url("/sub")))
            val options = NativeSubscriptionRequestOptions("Provider/1", "x-hwid: local-test\nCookie: private=test\nAuthorization: Bearer test")
            val request = options.request(first.url("/sub").toString())
            options.client(OkHttpClient(), request).newCall(request).execute().close()
            val observed = other.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("VetrOFF-Android", observed.getHeader("User-Agent"))
            listOf("x-hwid", "Cookie", "Authorization").forEach { assertNull(observed.getHeader(it)) }
        } }
    }
}
