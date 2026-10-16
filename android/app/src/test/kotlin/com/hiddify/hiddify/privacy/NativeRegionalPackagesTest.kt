package com.hiddify.hiddify.privacy

import org.junit.Assert.*
import org.junit.Test

class NativeRegionalPackagesTest {
    @Test fun pickerRetainsManualAndLegacyMissingChoicesButOnlySuggestsInstalledDefaults() {
        val installed = (1..1000).map { "com.example.app$it" }.toSet()
        val candidates = listOf("com.example.app1", "com.example.absent")
        assertEquals(setOf("com.example.app1"), NativeRegionalPackages.pickerSelection("", candidates, installed))
        assertEquals(setOf("com.example.app1", "com.example.legacy"),
            NativeRegionalPackages.pickerSelection("com.example.legacy", candidates, installed))
        assertEquals(setOf("com.example.absent"),
            NativeRegionalPackages.pickerSelection("manual:com.example.absent", candidates, installed))
        assertTrue(NativeRegionalPackages.pickerSelection("manual:", candidates, installed).isEmpty())
    }

    @Test fun sharedDirectCandidateRulePreservesAllAutomaticAndManualConflictCases() {
        val direct = listOf("com.example.shared", "com.example.bank")
        val proxy = listOf("com.example.shared", "com.example.chat")
        for (directManual in listOf(false, true)) for (proxyManual in listOf(false, true)) {
            assertEquals(if (proxyManual && !directManual) listOf("com.example.bank") else direct,
                NativeRegionalPackages.directCandidates(NativePackageSelection(directManual, direct),
                    NativePackageSelection(proxyManual, proxy)))
        }
    }

    @Test fun manualEmptyAndAutomaticEmptyAreDifferent() {
        val defaults = listOf("com.example.default")
        assertEquals(NativePackageSelection(true, emptyList()), NativeRegionalPackages.selection("manual:", defaults))
        assertEquals(NativePackageSelection(false, defaults), NativeRegionalPackages.selection("", defaults))
    }

    @Test fun legacyExtrasMergeWithoutDuplicates() {
        assertEquals(NativePackageSelection(false, listOf("com.example.default", "com.example.extra")),
            NativeRegionalPackages.selection("com.example.extra;com.example.default", listOf("com.example.default")))
    }

    @Test fun encodingIsDeterministicAndPreservesPackageCase() {
        val packages = setOf("com.example.Zebra", "com.example.Alpha")
        assertEquals("manual:com.example.Alpha,com.example.Zebra", NativeRegionalPackages.encodeManual(packages))
        assertEquals(packages, NativeRegionalPackages.selection(NativeRegionalPackages.encodeManual(packages), emptyList()).values.toSet())
    }

    @Test fun directSelectionRemovesOverlapsFromManualProxy() {
        val current = NativeRegionalPackageValues("", "manual:com.example.shared,com.example.proxy")
        val result = NativeRegionalPackages.replace(current, NativeRegionalAppKind.DIRECT, setOf("com.example.shared"))
        assertEquals("manual:com.example.shared", result.direct)
        assertEquals("manual:com.example.proxy", result.proxy)
        assertEquals("", current.direct)
    }

    @Test fun proxySelectionRemovesOverlapsFromManualDirect() {
        val result = NativeRegionalPackages.replace(NativeRegionalPackageValues("manual:com.example.shared", ""),
            NativeRegionalAppKind.PROXY, setOf("com.example.shared"))
        assertEquals("manual:", result.direct)
        assertEquals("manual:com.example.shared", result.proxy)
    }

    @Test fun oppositeAutomaticLegacySettingsAreRetained() {
        for (kind in NativeRegionalAppKind.entries) {
            val current = NativeRegionalPackageValues("com.example.direct", "com.example.proxy")
            val result = NativeRegionalPackages.replace(current, kind, emptySet())
            if (kind == NativeRegionalAppKind.DIRECT) assertEquals(current.proxy, result.proxy)
            else assertEquals(current.direct, result.direct)
        }
    }

    @Test fun resetAffectsOnlyChosenSide() {
        val current = NativeRegionalPackageValues("manual:com.example.direct", "manual:com.example.proxy")
        assertEquals(current.copy(direct = ""), NativeRegionalPackages.reset(current, NativeRegionalAppKind.DIRECT))
        assertEquals(current.copy(proxy = ""), NativeRegionalPackages.reset(current, NativeRegionalAppKind.PROXY))
    }

    @Test fun uninstalledManualPackagesSurviveOtherSelectionChanges() {
        val result = NativeRegionalPackages.replace(NativeRegionalPackageValues("manual:com.example.uninstalled", ""),
            NativeRegionalAppKind.PROXY, setOf("com.example.other"))
        assertEquals("manual:com.example.uninstalled", result.direct)
    }

    @Test fun rejectsNonPackagesAndTooManyChoices() {
        for (packages in listOf(setOf("localhost"), setOf("*.com.example.app"), setOf("com.example.app,com.example.other"),
            setOf("https://example.com"), (1..257).map { "com.example.app$it" }.toSet())) {
            assertThrows(IllegalArgumentException::class.java) { NativeRegionalPackages.encodeManual(packages) }
        }
        val max = (1..256).map { "com.example.app$it" }.toSet()
        assertEquals(max, NativeRegionalPackages.selection(NativeRegionalPackages.encodeManual(max), emptyList()).values.toSet())
    }
}
