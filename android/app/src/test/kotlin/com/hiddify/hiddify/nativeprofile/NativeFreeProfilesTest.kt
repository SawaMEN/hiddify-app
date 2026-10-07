package com.hiddify.hiddify.nativeprofile

import org.junit.Assert.*
import org.junit.Test

class NativeFreeProfilesTest {
    private fun feed(extra: String = "", region: String = "[]", url: String = "https://example.com/sub") =
        """{"profiles":[{"region":$region,"sublink":"$url","title":{"en":"Provider"},"tags":{"en":["Fast"]},"consent":{"en":"Consent"}$extra}]}"""

    @Test fun localizesWithFallbackAndFiltersRegions() {
        val all = NativeFreeProfiles.parse(feed()).single()
        assertEquals("Provider", all.title(true))
        assertEquals(listOf("Fast"), all.tags(true))
        assertEquals("Consent", all.consent(true))
        assertTrue(all.matches("ru"))
        val ru = NativeFreeProfiles.parse(feed(region = "[\"ru\"]")).single()
        assertTrue(ru.matches("ru"))
        assertFalse(ru.matches("other"))
    }

    @Test fun distinguishesMissingFeaturesFromExplicitDisabledFeatures() {
        assertNull(NativeFreeProfiles.parse(feed()).single().neededFeatures)
        assertEquals(emptySet<String>(), NativeFreeProfiles.parse(feed(",\"needed_features\":[]")).single().neededFeatures)
        assertEquals(setOf("fragment"), NativeFreeProfiles.parse(feed(",\"needed_features\":[\"fragment\"]")).single().neededFeatures)
    }

    @Test fun rejectsInvalidFeedAndNonSubscriptionUrls() {
        for (invalid in listOf("{}", feed(region = "[42]"), feed(url = "file:///tmp/sub"), feed(url = "https:///sub"))) {
            assertTrue(invalid, runCatching { NativeFreeProfiles.parse(invalid) }.isFailure)
        }
    }
}
