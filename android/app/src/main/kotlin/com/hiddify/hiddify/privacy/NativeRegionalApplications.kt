package com.hiddify.hiddify.privacy

import com.hiddify.hiddify.nativerouting.NativeInstalledApp

/** All installed apps are selectable, regardless of membership in the service catalogue. */
object NativeRegionalApplications {
    fun rows(installed: List<NativeInstalledApp>, selected: Set<String>,
        includeMissing: Boolean = false): List<NativeInstalledApp> {
        val missing = if (includeMissing) selected.map { NativeInstalledApp(it, it, false) } else emptyList()
        return (installed + missing)
            .distinctBy { it.packageName }
            .sortedWith(compareByDescending<NativeInstalledApp> { it.packageName in selected }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }.thenBy { it.packageName })
    }
}
