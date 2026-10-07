package com.hiddify.hiddify.privacy

import org.junit.Assert.*
import org.junit.Test

class NativeRegionalOptionsTest {
    @Test fun defaultsMatchCompatibilityUi() {
        val value = NativeRegionalOptions()
        assertEquals(NativeRegionalMode.RUSSIAN_BYPASS, value.mode)
        assertTrue(value.russianNetworkBypass && value.russianAppsBypass && value.restrictedServicesProxy)
        assertEquals("", value.directDomains)
        assertEquals("", value.proxyDomains)
    }

    @Test fun savedModesMapWithoutEnablingUnknownModes() {
        NativeRegionalMode.entries.forEach { assertEquals(it, NativeRegionalMode.fromValue(it.value)) }
        assertEquals(NativeRegionalMode.OFF, NativeRegionalMode.fromValue("unexpected"))
    }

    @Test fun overridesDisableEffectiveModeAndPreserveSavedChoices() {
        val value = NativeRegionalOptions(mode = NativeRegionalMode.SELECTED_PROXY)
        assertEquals(NativeRegionalMode.SELECTED_PROXY, value.effectiveMode(false, false))
        for ((full, manual) in listOf(true to false, false to true, true to true)) {
            assertEquals(NativeRegionalMode.OFF, value.effectiveMode(full, manual))
            assertEquals(NativeRegionalMode.SELECTED_PROXY, value.mode)
        }
    }

    @Test fun normalizesDomainSeparatorsWildcardsAndDuplicates() {
        val initial = NativeRegionalOptions(directDomains = "*.Example.COM; .example.com., TEST.org\n",
            proxyDomains = "proxy.example.com proxy.example.com")
        val result = initial.validated()
        assertEquals("example.com\ntest.org", result.directDomains)
        assertEquals("proxy.example.com", result.proxyDomains)
        assertEquals(result, result.validated())
        assertTrue(initial.directDomains.startsWith("*."))
    }

    @Test fun rejectsUrlsPortsIpLiteralsAndInvalidNames() {
        for (entry in listOf("https://example.com", "example.com:443", "127.0.0.1", "[::1]", "localhost",
            "example.com/path", "example.com?token=x", "evil@example.com")) {
            assertThrows(IllegalArgumentException::class.java) { NativeRegionalOptions(directDomains = entry).validated() }
            assertThrows(IllegalArgumentException::class.java) { NativeRegionalOptions(proxyDomains = entry).validated() }
        }
    }

    @Test fun rejectsOversizedInputAndTooManyEntries() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeRegionalOptions(directDomains = " ".repeat(8193)).validated()
        }
        val values = (1..256).map { "d$it.example.com" }
        assertEquals(256, NativeRoutingTokens.parse(values.joinToString(","), true).size)
        assertThrows(IllegalArgumentException::class.java) {
            NativeRoutingTokens.parse((values + "overflow.example.com").joinToString(","), true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeRoutingTokens.parse("a".repeat(250) + ".com", true)
        }
    }

    @Test fun mergedCatalogueLimitCountsUniqueEntries() {
        val builtIn = (1..256).map { "d$it.example.com" }
        assertEquals(builtIn, NativeRoutingTokens.merge(builtIn, "D1.example.com", true))
        assertThrows(IllegalArgumentException::class.java) {
            NativeRoutingTokens.merge(builtIn, "new.example.com", true)
        }
    }

    @Test fun packageNamesDoNotUseDomainWildcardNormalization() {
        assertEquals(listOf("com.example.app"), NativeRoutingTokens.parse("com.example.app", false))
        assertThrows(IllegalArgumentException::class.java) { NativeRoutingTokens.parse("*.com.example.app", false) }
    }
}
