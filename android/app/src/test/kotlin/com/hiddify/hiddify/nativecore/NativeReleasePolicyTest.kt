package com.hiddify.hiddify.nativecore

import org.junit.Assert.*
import org.junit.Test

class NativeReleasePolicyTest {
    @Test fun acceptsOnlyProductionVersionAndBoundedBuildTags() {
        assertEquals("1.0.1" to 40201, NativeReleasePolicy.productionTag("v1.0.1+40201.prod"))
        assertEquals("1.0.1" to 0, NativeReleasePolicy.productionTag("v1.0.1"))
        listOf("v1.0.1-rc.1", "v1.0.1-beta", "v1.0.1+oops", "v1.0.1+999999999999",
            "v1.-1.0", "v1.0", "v999999999999.0.0").forEach { assertNull(it, NativeReleasePolicy.productionTag(it)) }
    }
    @Test fun trustedReleasePagesRejectCredentialsPortsAndTraversal() {
        val prefix = "/SawaMEN/hiddify-app/releases/"
        assertTrue(NativeReleasePolicy.trustedUrl("https://github.com${prefix}tag/v1.0.1", prefix))
        listOf("https://user@github.com${prefix}tag/v1", "https://github.com:123${prefix}tag/v1",
            "https://github.com${prefix}../../other", "https://github.com${prefix}%2e%2e/%2e%2e/other",
            "https://github.com.evil${prefix}tag/v1").forEach { assertFalse(it, NativeReleasePolicy.trustedUrl(it, prefix)) }
    }
}
