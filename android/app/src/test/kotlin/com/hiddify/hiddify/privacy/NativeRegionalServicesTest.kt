package com.hiddify.hiddify.privacy

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class NativeRegionalServicesTest {
    private val fixture = """{"proxyServices":[
        {"id":"video","name":"Video","packages":["com.example.video"],"domains":["video.example","cdn.example"]},
        {"id":"chat","name":"Chat","packages":["com.example.chat"],"domains":["chat.example","cdn.example"]}]}"""

    @Test fun defaultsIncludeAllServicesButManualEmptyIncludesNone() {
        val services = NativeRegionalServices.catalogue(fixture)
        assertEquals(setOf("video", "chat"), NativeRegionalServices.selection("", services))
        assertEquals(emptySet<String>(), NativeRegionalServices.selection("manual:", services))
        assertEquals(emptyList<String>(), NativeRegionalServices.domains(services, emptySet()))
    }

    @Test fun selectedServiceRoutesOnlyItsDomainsAndSuggestedApplications() {
        val services = NativeRegionalServices.catalogue(fixture)
        assertEquals(listOf("video.example", "cdn.example"), NativeRegionalServices.domains(services, setOf("video")))
        assertEquals(listOf("com.example.video"), NativeRegionalServices.packages(services, setOf("video")))
        assertEquals(3, NativeRegionalServices.domains(services, setOf("video", "chat")).size)
    }

    @Test fun sharedCdnRemainsUntilItsLastServiceIsDeselected() {
        val services = NativeRegionalServices.catalogue(fixture)
        assertEquals(listOf("chat.example", "cdn.example"), NativeRegionalServices.domains(services, setOf("chat")))
    }

    @Test fun manualChoicesRoundTripAndRetainUnavailableIdsWithoutAddingRoutes() {
        val services = NativeRegionalServices.catalogue(fixture)
        val ids = setOf("chat", "removed-service")
        assertEquals(ids, NativeRegionalServices.selection(NativeRegionalServices.encode(ids), services))
        assertEquals(emptyList<String>(), NativeRegionalServices.domains(services, setOf("removed-service")))
    }

    @Test fun rejectsInvalidSelectionsAndDuplicateServices() {
        for (stored in listOf("video", "manual:https://example.com", "manual:video chat", "a".repeat(8193)))
            assertThrows(IllegalArgumentException::class.java) { NativeRegionalServices.selection(stored, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { NativeRegionalServices.catalogue(fixture.replace("\"chat\"", "\"video\"")) }
        assertThrows(IllegalArgumentException::class.java) { NativeRegionalServices.encode((0..64).map { "id$it" }.toSet()) }
    }

    @Test fun bundledServiceGroupsCoverExactlyTheLegacyCatalogue() {
        val candidates = listOf(File("android/app/src/main/assets/region_routing.json"), File("../../android/app/src/main/assets/region_routing.json"))
        val json = candidates.first { it.isFile }.readText()
        val services = NativeRegionalServices.catalogue(json)
        val root = com.google.gson.JsonParser.parseString(json).asJsonObject
        val ids = NativeRegionalServices.selection("", services)
        assertEquals(root.getAsJsonArray("proxyDomains").map { it.asString }.toSet(), NativeRegionalServices.domains(services, ids).toSet())
        assertEquals(root.getAsJsonArray("proxyPackages").map { it.asString }.toSet(), NativeRegionalServices.packages(services, ids).toSet())
        assertEquals(15, services.size)
        assertTrue(NativeRegionalServices.domains(services, setOf("youtube")).contains("googlevideo.com"))
    }

    @Test fun savingServiceChoicesPreservesEveryRoutingModeAndIndependentAppChoices() {
        val current = NativeRegionalPackageValues("manual:com.example.bank", "manual:com.example.chat")
        for (mode in NativeRegionalMode.entries) {
            val before = mapOf("flutter.privacy-routing-mode" to mode.value,
                "flutter.privacy-direct-packages" to current.direct, "flutter.privacy-proxy-packages" to current.proxy)
            val changes = NativeRegionalServices.changes(current, "", NativeRegionalAppKind.PROXY,
                NativeRegionalAppChoices(services = setOf("video")))
            val after = before + changes
            assertEquals(mode.value, after["flutter.privacy-routing-mode"])
            assertEquals(current.direct, after["flutter.privacy-direct-packages"])
            assertEquals(current.proxy, after["flutter.privacy-proxy-packages"])
            assertEquals("manual:video", after["flutter.privacy-proxy-services"])
        }
    }

    @Test fun resetRestoresDefaultsOnlyForTheChosenSideAndPreservesServiceChoicesForDirectApps() {
        val current = NativeRegionalPackageValues("manual:com.example.bank", "manual:com.example.chat")
        val direct = NativeRegionalServices.changes(current, "manual:video", NativeRegionalAppKind.DIRECT, NativeRegionalAppChoices(reset = true))
        assertEquals("", direct["flutter.privacy-direct-packages"])
        assertEquals(current.proxy, direct["flutter.privacy-proxy-packages"])
        assertFalse(direct.containsKey("flutter.privacy-proxy-services"))
        val proxy = NativeRegionalServices.changes(current, "manual:video", NativeRegionalAppKind.PROXY, NativeRegionalAppChoices(reset = true))
        assertEquals(current.direct, proxy["flutter.privacy-direct-packages"])
        assertEquals("", proxy["flutter.privacy-proxy-packages"])
        assertEquals("", proxy["flutter.privacy-proxy-services"])
    }
    @Test fun directNetworkAndDomainsSaveTogetherWithoutChangingModeOrProxyChoices() {
        for (mode in NativeRegionalMode.entries) {
            val current = NativeRegionalOptions(mode = mode, proxyDomains = "vpn.example")
            val changed = NativeRegionalServices.options(current, NativeRegionalAppKind.DIRECT,
                NativeRegionalAppChoices(russianNetworkBypass = false, domains = "BANK.example, direct.example"))
            assertEquals(mode, changed.mode)
            assertFalse(changed.russianNetworkBypass)
            assertEquals("bank.example\ndirect.example", changed.directDomains)
            assertEquals(current.proxyDomains, changed.proxyDomains)
            assertEquals(current, NativeRegionalServices.options(current, NativeRegionalAppKind.DIRECT,
                NativeRegionalAppChoices(reset = true)))
        }
    }

    @Test fun proxyDomainEditsCannotChangeRussianNetworkOrDirectDomains() {
        val current = NativeRegionalOptions(russianNetworkBypass = false, directDomains = "bank.example")
        val changed = NativeRegionalServices.options(current, NativeRegionalAppKind.PROXY,
            NativeRegionalAppChoices(domains = "video.example"))
        assertEquals(current.copy(proxyDomains = "video.example"), changed)
        assertThrows(IllegalArgumentException::class.java) {
            NativeRegionalServices.options(current, NativeRegionalAppKind.PROXY,
                NativeRegionalAppChoices(russianNetworkBypass = true))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeRegionalServices.options(current, NativeRegionalAppKind.DIRECT,
                NativeRegionalAppChoices(russianNetworkBypass = true, domains = "https://invalid.example"))
        }
    }

}
