package com.hiddify.hiddify.privacy

import org.junit.Assert.*
import org.junit.Test

class NativeCommunityListsTest {
    @Test fun providerKeysAreSelectionsAndDomainsAreOnlySearchMetadata() {
        val services = NativeCommunityLists.parse("""{"copilot":["copilot.microsoft.com"],"youtube.com":["googlevideo.com","ytimg.com"]}""")
        assertEquals(listOf("copilot", "youtube.com"), services.map { it.id })
        assertEquals("copilot.microsoft.com", services.first().domains.single())
        assertEquals("copilot,youtube.com", NativeCommunityLists.selection(false, services.map { it.id }.toSet()).sites)
    }

    @Test fun clearingAllDisablesSourceInsteadOfRoutingEveryService() {
        assertEquals(NativeCommunitySelection(false, ""), NativeCommunityLists.selection(false, emptySet()))
        assertEquals(NativeCommunitySelection(true, ""), NativeCommunityLists.selection(true, emptySet()))
    }

    @Test fun deselectingFromAllMaterializesRemainingServicesAndRetainsUnavailableSelections() {
        val selected = NativeCommunityLists.toggle(true, setOf("old-service"), setOf("youtube.com", "discord.com"), "youtube.com", false)
        assertEquals(setOf("discord.com", "old-service"), selected)
        assertEquals(NativeCommunitySelection(true, "discord.com,old-service"), NativeCommunityLists.selection(false, selected))
    }

    @Test fun removingLastServiceProducesDisabledSelection() {
        val selected = NativeCommunityLists.toggle(false, setOf("youtube.com"), emptySet(), "youtube.com", false)
        assertFalse(NativeCommunityLists.selection(false, selected).enabled)
    }

    @Test fun existingSeparatorsAndDuplicateSelectionsArePreservedCanonically() {
        assertEquals(setOf("youtube.com", "discord.com", "copilot"), NativeCommunityLists.selected("YouTube.com; discord.com\ncopilot,YouTube.com"))
    }

    @Test fun errorResponsesCannotReplaceValidCatalogues() {
        for (text in listOf("{}", "[]", "<html>404</html>", "{\"error\":\"unavailable\"}", "{\"youtube.com\":[42]}")) {
            assertThrows(Exception::class.java) { NativeCommunityLists.parse(text) }
        }
    }

    @Test fun unsafeIdentifiersCannotInjectAdditionalQueryFilters() {
        assertThrows(IllegalArgumentException::class.java) { NativeCommunityLists.selection(false, setOf("site&format=text")) }
        assertThrows(IllegalArgumentException::class.java) { NativeCommunityLists.parse("""{"site&format=text":["example.org"]}""") }
    }

    @Test fun networkOnlyServicesRemainSelectableWithoutExampleDomains() {
        assertEquals(listOf(NativeCommunityService("network-service", emptyList())),
            NativeCommunityLists.parse("""{"network-service":[]}"""))
    }

    @Test fun catalogueURLsUseMatchingSourcesAndOnlyDomainMetadata() {
        assertEquals("https://iplist.my-handbook.ru/?format=json&data=domains", NativeCommunitySource.VPN.catalogueUrl)
        assertEquals("https://ru-iplist.my-handbook.ru/?format=json&data=domains", NativeCommunitySource.DIRECT.catalogueUrl)
    }
}
