package com.hiddify.hiddify.nativecore

import com.hiddify.hiddify.nativeconnection.NativeHealthEndpoints
import org.junit.Assert.*
import org.junit.Test

class NativeHealthEndpointsTest {
    @Test fun httpPortalAndInvalidInputNeverBecomeHealthEndpoints() {
        for (url in listOf("http://captive.apple.com/hotspot-detect.html", "https://user:secret@example.com/", "https://a/#secret", "https://a:70000/", "bad")) {
            assertFalse(NativeHealthEndpoints.urls(url).contains(url))
        }
    }
    @Test fun customHttpsIsPreservedWithIndependentFallbackHosts() {
        val urls = NativeHealthEndpoints.urls("https://example.com/check")
        assertEquals("https://example.com/check", urls.first())
        assertEquals(3, urls.size)
        assertEquals(3, urls.map { java.net.URI(it).host }.distinct().size)
    }
    @Test fun builtInEndpointIsNotDuplicated() {
        val url = "https://www.gstatic.com/generate_204"
        assertEquals(1, NativeHealthEndpoints.urls(url).count { it == url })
    }
}
