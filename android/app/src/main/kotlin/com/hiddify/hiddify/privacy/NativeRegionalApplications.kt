package com.hiddify.hiddify.privacy

import com.hiddify.hiddify.nativerouting.NativeInstalledApp

/** Show the complete offline catalogue, even when only a few apps are installed. */
object NativeRegionalApplications {
    fun rows(installed: List<NativeInstalledApp>, services: List<NativeRegionalService>,
        selected: Set<String>): List<NativeInstalledApp> {
        val catalogue = services.flatMap { service ->
            service.packages.map { NativeInstalledApp(it, service.name, false) }
        }
        return (installed + catalogue + selected.map { NativeInstalledApp(it, it, false) })
            .distinctBy { it.packageName }
            .sortedWith(compareByDescending<NativeInstalledApp> { it.packageName in selected }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }.thenBy { it.packageName })
    }
}
