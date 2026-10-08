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

    @Test fun groupFeedAssignsCategoriesByServiceIdWithoutReplacingRouteIdentifiers() {
        val services = NativeCommunityLists.parse("""{"copilot":["copilot.microsoft.com"],"discord.com":["discord.gg"]}""")
        val grouped = NativeCommunityLists.withGroups(services,
            NativeCommunityLists.parseGroups("""{"copilot":"AI","discord.com":"messengers","extra":"cdn"}"""))
        assertEquals(listOf("ai", "messengers"), grouped.map { it.group })
        assertEquals(listOf("copilot", "discord.com"), grouped.map { it.id })
    }

    @Test fun categoryCacheRoundTripAndLegacyArrayCacheRemainCompatible() {
        val services = listOf(NativeCommunityService("discord.com", listOf("discord.gg"), "messengers"),
            NativeCommunityService("network-service", emptyList(), "cdn"))
        assertEquals(services, NativeCommunityLists.parse(NativeCommunityLists.encodeCache(services)))
        assertEquals("", NativeCommunityLists.parse("""{"copilot":["copilot.microsoft.com"]}""").single().group)
    }

    @Test fun selectingPartiallyCheckedCategorySelectsEveryMemberAndPreservesOtherChoices() {
        val selected = NativeCommunityLists.toggleGroup(false, setOf("discord.com", "youtube.com", "old-service"),
            setOf("discord.com", "telegram.org", "youtube.com"), setOf("discord.com", "telegram.org"), true)
        assertEquals(setOf("discord.com", "telegram.org", "youtube.com", "old-service"), selected)
    }

    @Test fun deselectingCategoryFromAllPreservesOtherCategoriesAndUnavailableChoices() {
        val selected = NativeCommunityLists.toggleGroup(true, setOf("old-service"),
            setOf("discord.com", "telegram.org", "youtube.com"), setOf("discord.com", "telegram.org"), false)
        assertEquals(setOf("youtube.com", "old-service"), selected)
        assertEquals(NativeCommunitySelection(true, "old-service,youtube.com"), NativeCommunityLists.selection(false, selected))
    }

    @Test fun clearingLastCategoryDisablesSource() {
        val selected = NativeCommunityLists.toggleGroup(false, setOf("discord.com", "telegram.org"),
            emptySet(), setOf("discord.com", "telegram.org"), false)
        assertEquals(NativeCommunitySelection(false, ""), NativeCommunityLists.selection(false, selected))
    }

    @Test fun invalidGroupFeedCannotOverwriteSavedGroups() {
        for (text in listOf("{}", "[]", "{\"error\":true}", "{\"copilot\":null}", "{\"copilot\":\"<html>\"}")) {
            assertThrows(Exception::class.java) { NativeCommunityLists.parseGroups(text) }
        }
    }

    @Test fun localDomainsAreShownForSelectedServiceWithoutBecomingProviderIds() {
        val repo = NativeCommunityLists.parse("""{"repo":["github.com"]}""").single()
        assertEquals("repo", repo.id)
        assertEquals(listOf("docker.io", "github.com"), repo.domains)
        val porn = NativeCommunityLists.parse("""{"pornhub.com":["pornhub.com","phncdn.com"]}""").single()
        assertEquals(listOf("phncdn.com", "pornhub.org", "pornhub.com"), porn.domains)
        assertEquals("pornhub.com,repo", NativeCommunityLists.selection(false, setOf(repo.id, porn.id)).sites)
        assertEquals(listOf("youtube.com"), NativeCommunityLists.parse("""{"youtube.com":["youtube.com"]}""").single().domains)
        assertEquals(listOf(repo, porn).sortedBy { it.id },
            NativeCommunityLists.parse(NativeCommunityLists.encodeCache(listOf(repo, porn))))
    }
}
