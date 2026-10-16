package com.hiddify.hiddify.privacy

import org.junit.Assert.*
import org.junit.Test

class NativeRegionalOptionsTest {
    @Test fun selectedServicesGenerateIdenticalCoreNetworkInputsForEitherRussianDomainSetting() {
        val options = NativeRegionalOptions(mode = NativeRegionalMode.SELECTED_PROXY,
            directDomains = "selected.example.ru", proxyDomains = "selected.example.ru")
        val expected = mapOf<String, Any>("region" to "other",
            "privacy-russian-network-bypass" to false, "privacy-direct-domains" to emptyList<String>())
        for (enabled in listOf(false, true)) {
            val selected = options.copy(russianNetworkBypass = enabled)
            assertEquals(expected, selected.networkPolicy(listOf("example.ru", "russian.example")))
            assertEquals(options.proxyDomains, selected.proxyDomains)
        }
    }

    @Test fun networkPolicyRetainsRussianBypassBehaviorInOtherModes() {
        val options = NativeRegionalOptions(directDomains = "custom.example")
        for (mode in listOf(NativeRegionalMode.RUSSIAN_BYPASS, NativeRegionalMode.OFF)) {
            val active = options.withMode(mode).networkPolicy(listOf("bank.example"))
            assertEquals("ru", active["region"])
            assertEquals(true, active["privacy-russian-network-bypass"])
            assertEquals(listOf("bank.example", "custom.example"), active["privacy-direct-domains"])
            val disabled = options.withMode(mode).copy(russianNetworkBypass = false).networkPolicy(listOf("bank.example"))
            assertEquals("other", disabled["region"])
            assertEquals(false, disabled["privacy-russian-network-bypass"])
            assertEquals(if (mode == NativeRegionalMode.RUSSIAN_BYPASS) listOf("custom.example") else emptyList<String>(),
                disabled["privacy-direct-domains"])
        }
    }

    @Test fun selectedServicesCannotBeOverriddenBySavedRussianNetworkBypass() {
        for (saved in listOf(false, true)) {
            val selected = NativeRegionalOptions(mode = NativeRegionalMode.SELECTED_PROXY,
                russianNetworkBypass = saved)
            assertFalse(selected.russianNetworkBypassActive)
            // Preserve the saved preference when switching back to other routing modes.
            for (mode in listOf(NativeRegionalMode.RUSSIAN_BYPASS, NativeRegionalMode.OFF))
                assertEquals(saved, selected.withMode(mode).russianNetworkBypassActive)
            for ((full, community) in listOf(true to false, false to true)) {
                val effective = selected.withMode(selected.effectiveMode(full, community))
                assertEquals(saved, effective.russianNetworkBypassActive)
            }
        }
    }

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
    @Test fun supportsInternationalDomainsAndRealPackagesWithoutAcceptingInvalidLabels() {
        assertEquals(listOf("xn--e1afmkfd.xn--p1ai"), NativeRoutingTokens.parse("пример.рф", true))
        assertEquals(listOf("android"), NativeRoutingTokens.parse("android", false))
        listOf("-bad.example", "bad-.example", "bad_name.example", "a".repeat(64) + ".example").forEach {
            assertTrue(it, runCatching { NativeRoutingTokens.parse(it, true) }.isFailure)
        }
        listOf("1com.example", "com.bad-name").forEach {
            assertTrue(it, runCatching { NativeRoutingTokens.parse(it, false) }.isFailure)
        }
    }

    @Test fun switchingModeSetsConsistentDefaultsAndRetainsCustomDomainDrafts() {
        val original = NativeRegionalOptions(directDomains = "direct.example", proxyDomains = "proxy.example")
        val selective = original.withMode(NativeRegionalMode.SELECTED_PROXY)
        assertTrue(selective.russianNetworkBypass)
        assertFalse(selective.russianAppsBypass)
        assertTrue(selective.restrictedServicesProxy)
        assertEquals("proxy.example", selective.proxyDomains)
        val bypass = selective.withMode(NativeRegionalMode.RUSSIAN_BYPASS)
        assertTrue(bypass.russianNetworkBypass && bypass.russianAppsBypass && bypass.restrictedServicesProxy)
        assertFalse(bypass.withMode(NativeRegionalMode.OFF).restrictedServicesProxy)
    }

    @Test fun modeIsTheOnlySourceOfApplicationRulesAndNetworkStaysIndependent() {
        for (mode in NativeRegionalMode.entries) for (full in listOf(false, true)) for (community in listOf(false, true)) {
            val saved = NativeRegionalOptions(mode = mode, russianNetworkBypass = true)
            val effective = saved.withMode(saved.effectiveMode(full, community))
            assertTrue(effective.russianNetworkBypass)
            assertEquals(!full && !community && mode == NativeRegionalMode.RUSSIAN_BYPASS, effective.russianAppsBypass)
            assertEquals(!full && !community && mode != NativeRegionalMode.OFF, effective.restrictedServicesProxy)
            assertFalse(effective.copy(russianNetworkBypass = false).russianNetworkBypass)
        }
    }

    @Test fun changingAppModePreservesIndependentNetworkSwitchAndDomains() {
        for (enabled in listOf(false, true)) for (mode in NativeRegionalMode.entries) {
            val options = NativeRegionalOptions(russianNetworkBypass = enabled, directDomains = "example.ru")
            val changed = options.withMode(mode)
            assertEquals(enabled, changed.russianNetworkBypass)
            assertEquals("example.ru", changed.directDomains)
        }
    }

    @Test fun explicitDeviceModesDisableFullConfigRulesButKeepProfileAndTransportSettings() {
        val json = """{"execute-config-as-is":true,"enable-full-config":true,"mtu":1400,"tls-tricks":{"enable-fragment":true}}"""
        for (mode in NativeRegionalMode.entries) {
            val root = com.google.gson.JsonParser.parseString(NativeRegionalOptions(mode = mode).managedCoreOptions(json)).asJsonObject
            assertFalse(root["execute-config-as-is"].asBoolean)
            assertFalse(root["enable-full-config"].asBoolean)
            assertEquals(1400, root["mtu"].asInt)
            assertTrue(root["tls-tricks"].asJsonObject["enable-fragment"].asBoolean)
        }
        assertEquals(json, NativeRegionalOptions(mode = NativeRegionalMode.OFF, russianNetworkBypass = false).managedCoreOptions(json))
    }
    @Test fun communityModeDisablesFullProfileRulesWithNetworkExceptionOff() {
        val original = """{"execute-config-as-is":true,"enable-full-config":true,"mtu":1400}"""
        val options = NativeRegionalOptions(mode = NativeRegionalMode.OFF, russianNetworkBypass = false)
        val root = com.google.gson.JsonParser.parseString(options.managedCoreOptions(original, true)).asJsonObject
        assertFalse(root["execute-config-as-is"].asBoolean)
        assertFalse(root["enable-full-config"].asBoolean)
        assertEquals(1400, root["mtu"].asInt)
    }

}
