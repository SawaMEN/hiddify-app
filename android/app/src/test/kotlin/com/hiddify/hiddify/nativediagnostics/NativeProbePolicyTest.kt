package com.hiddify.hiddify.nativediagnostics

import org.junit.Assert.*
import org.junit.Test

class NativeProbePolicyTest {
    @Test fun acceptsNormalSuccessResponses() {
        assertTrue(NativeProbePolicy.healthy("https://probe.example/", 200))
        assertTrue(NativeProbePolicy.healthy("https://probe.example/", 204))
    }

    @Test fun doesNotAcceptCaptivePortalFor204Endpoints() {
        for (path in listOf("generate_204", "generate204")) {
            assertFalse(NativeProbePolicy.healthy("https://probe.example/$path", 200))
            assertTrue(NativeProbePolicy.healthy("https://probe.example/$path", 204))
        }
    }

    @Test fun rejectsRedirectAndErrorResponses() {
        for (status in listOf(0, 201, 301, 302, 307, 401, 407, 500)) {
            assertFalse(NativeProbePolicy.healthy("https://probe.example/", status))
        }
    }

    @Test fun rejectsInvalidOrCredentialBearingUrls() {
        for (url in listOf("", "file:///tmp/profile", "https:///no-host", "https://user:password@probe.example/",
            "https://probe.example/#token", "https://probe.example:0/", "https://probe.example:65536/")) {
            assertFalse(url, NativeProbePolicy.validUrl(url))
            assertFalse(url, NativeProbePolicy.healthy(url, 204))
        }
    }

    @Test fun acceptsIpv6AndCustomPorts() {
        assertTrue(NativeProbePolicy.validUrl("https://[2001:db8::1]:2096/generate_204"))
        assertTrue(NativeProbePolicy.validUrl("http://probe.example:8080/"))
    }

    @Test fun usesOnlyThePathToIdentify204Endpoints() {
        assertTrue(NativeProbePolicy.healthy("https://probe.example/check?name=generate_204", 200))
    }
}
