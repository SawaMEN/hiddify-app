package com.hiddify.hiddify.privacy

import android.content.Context
import com.hiddify.hiddify.Settings
import com.hiddify.hiddify.nativerouting.NativeInstalledApp
import com.hiddify.hiddify.nativerouting.NativeInstalledApps

data class NativeRegionalAppSnapshot(
    val kind: NativeRegionalAppKind,
    val apps: List<NativeInstalledApp>,
    val selected: Set<String>,
    val manual: Boolean,
    val missing: Set<String>,
)

class NativeRegionalRepository(private val context: Context) {
    fun load(): NativeRegionalOptions = NativeRegionalOptions(
        mode = NativeRegionalMode.fromValue(Settings.privacyRoutingMode),
        russianNetworkBypass = Settings.privacyRussianNetworkBypass,
        russianAppsBypass = Settings.privacyRussianAppsBypass,
        restrictedServicesProxy = Settings.privacyRestrictedServicesProxy,
        directDomains = Settings.privacyDirectDomains,
        proxyDomains = Settings.privacyProxyDomains,
    )

    /** Validate the merged catalogue before committing any preference. Call on IO. */
    fun save(value: NativeRegionalOptions): NativeRegionalOptions {
        val valid = value.validated()
        RegionalRouting.policy(context, options = valid)
        val editor = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).edit()
            .putString("flutter.privacy-routing-mode", valid.mode.value)
            .putBoolean("flutter.privacy-russian-network-bypass", valid.russianNetworkBypass)
            .putBoolean("flutter.privacy-russian-apps-bypass", valid.russianAppsBypass)
            .putBoolean("flutter.privacy-restricted-services-proxy", valid.restrictedServicesProxy)
            .putString("flutter.privacy-direct-domains", valid.directDomains)
            .putString("flutter.privacy-proxy-domains", valid.proxyDomains)
        check(editor.commit()) { "Could not save regional routing" }
        return load()
    }

    fun applicationSnapshot(kind: NativeRegionalAppKind): NativeRegionalAppSnapshot {
        val policy = RegionalRouting.policy(context)
        val stored = if (kind == NativeRegionalAppKind.DIRECT) Settings.privacyDirectPackages else Settings.privacyProxyPackages
        val selection = NativeRegionalPackages.selection(stored, emptyList())
        val key = if (kind == NativeRegionalAppKind.DIRECT) "privacy-configured-direct-packages" else "privacy-configured-proxy-packages"
        val configured = (policy[key] as? List<*>)?.filterIsInstance<String>().orEmpty()
        // Keep uninstalled manual/legacy choices; automatic defaults reflect packages present on this device.
        val selected = (if (selection.manual) selection.values else configured + selection.values)
            .filterNot { it == context.packageName }.toSet()
        val installed = NativeInstalledApps(context).load().filter { it.packageName != "android" }
        val missing = selected - installed.map { it.packageName }.toSet()
        val apps = (installed + missing.map { NativeInstalledApp(it, it, false) })
            .sortedWith(compareByDescending<NativeInstalledApp> { it.packageName in selected }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }.thenBy { it.packageName })
        return NativeRegionalAppSnapshot(kind, apps, selected, selection.manual, missing)
    }

    /** null restores automatic suggestions; an empty set explicitly selects no applications. */
    fun saveApplications(kind: NativeRegionalAppKind, selected: Set<String>?): NativeRegionalAppSnapshot {
        require(selected == null || context.packageName !in selected) { "The VPN client cannot route itself" }
        val current = NativeRegionalPackageValues(Settings.privacyDirectPackages, Settings.privacyProxyPackages)
        val next = if (selected == null) NativeRegionalPackages.reset(current, kind)
            else NativeRegionalPackages.replace(current, kind, selected)
        val options = load().copy(mode = NativeRegionalMode.RUSSIAN_BYPASS)
        RegionalRouting.policy(context, options = options, packages = next)
        check(context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).edit()
            .putString("flutter.privacy-routing-mode", options.mode.value)
            .putString("flutter.privacy-direct-packages", next.direct)
            .putString("flutter.privacy-proxy-packages", next.proxy)
            .commit()) { "Could not save regional applications" }
        return applicationSnapshot(kind)
    }
}
