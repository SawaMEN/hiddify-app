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
    val services: List<NativeRegionalService> = emptyList(),
    val selectedServices: Set<String> = emptySet(),
    val servicesManual: Boolean = false,
    val russianNetworkBypass: Boolean = true,
    val domains: String = "",
)

class NativeRegionalRepository(private val context: Context) {
    fun load(): NativeRegionalOptions = NativeRegionalOptions(
        mode = NativeRegionalMode.fromValue(Settings.privacyRoutingMode),
        russianNetworkBypass = Settings.privacyRussianNetworkBypass,
        directDomains = Settings.privacyDirectDomains,
        proxyDomains = Settings.privacyProxyDomains,
    )

    /** Validate the merged catalogue before committing any preference. Call on IO. */
    fun save(value: NativeRegionalOptions, useCommunityLists: Boolean = false, selectRouting: Boolean = true): NativeRegionalOptions {
        val valid = value.validated()
        RegionalRouting.policy(context, options = valid,
            fullTunnel = if (selectRouting) false else Settings.privacyFullTunnel,
            handbookRouting = if (selectRouting) useCommunityLists else Settings.handbookRouting)
        val editor = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).edit()
            .putString("flutter.privacy-routing-mode", valid.mode.value)
            .putBoolean("flutter.privacy-russian-network-bypass", valid.russianNetworkBypass)
            .putString("flutter.privacy-direct-domains", valid.directDomains)
            .putString("flutter.privacy-proxy-domains", valid.proxyDomains)
        if (selectRouting) {
            // A mode choice must apply, rather than remain masked by other routing engines.
            editor.putBoolean("flutter.privacy-full-tunnel", false)
                .putBoolean("flutter.handbook-routing", useCommunityLists)
                .putString("flutter.per_app_proxy_mode", "off")
        }
        if (valid.mode != NativeRegionalMode.OFF || valid.russianNetworkBypass || useCommunityLists) {
            editor.putBoolean("flutter.execute-config-as-is", false)
                .putString("config_options_json", valid.managedCoreOptions(Settings.configOptions, useCommunityLists))
        }
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
        val services = if (kind == NativeRegionalAppKind.PROXY) RegionalRouting.serviceCatalogue(context) else emptyList()
        val apps = NativeRegionalApplications.rows(installed, selected, includeMissing = kind == NativeRegionalAppKind.DIRECT)
            .filterNot { it.packageName == context.packageName }
        val missing = apps.map { it.packageName }.toSet() - installed.map { it.packageName }.toSet()
        val selectedServices = if (kind == NativeRegionalAppKind.PROXY)
            NativeRegionalServices.selection(Settings.privacyProxyServices, services) else emptySet()
        val rows = services + (selectedServices - services.map { it.id }.toSet()).map {
            NativeRegionalService(it, it, emptyList(), emptyList())
        }
        return NativeRegionalAppSnapshot(kind, apps, selected, selection.manual, missing,
            rows, selectedServices, kind == NativeRegionalAppKind.PROXY && Settings.privacyProxyServices.startsWith(NativeRegionalPackages.MANUAL_PREFIX),
            Settings.privacyRussianNetworkBypass,
            if (kind == NativeRegionalAppKind.DIRECT) Settings.privacyDirectDomains else Settings.privacyProxyDomains)
    }

    /** Null choices leave that list unchanged; reset restores suggestions, empty sets select nothing. */
    fun saveApplications(kind: NativeRegionalAppKind, choices: NativeRegionalAppChoices): NativeRegionalAppSnapshot {
        val selected = choices.packages
        require(selected == null || context.packageName !in selected) { "The VPN client cannot route itself" }
        val current = NativeRegionalPackageValues(Settings.privacyDirectPackages, Settings.privacyProxyPackages)
        val changes = NativeRegionalServices.changes(current, Settings.privacyProxyServices, kind, choices)
        val next = NativeRegionalPackageValues(changes.getValue("flutter.privacy-direct-packages"),
            changes.getValue("flutter.privacy-proxy-packages"))
        val services = changes["flutter.privacy-proxy-services"] ?: Settings.privacyProxyServices
        val options = NativeRegionalServices.options(load(), kind, choices)
        RegionalRouting.policy(context, options = options, packages = next, servicesStored = services)
        val editor = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).edit()
        changes.forEach { (key, value) -> editor.putString(key, value) }
        editor.putBoolean("flutter.privacy-russian-network-bypass", options.russianNetworkBypass)
            .putString("flutter.privacy-direct-domains", options.directDomains)
            .putString("flutter.privacy-proxy-domains", options.proxyDomains)
        if (options.mode != NativeRegionalMode.OFF || options.russianNetworkBypass || Settings.handbookRouting) {
            editor.putBoolean("flutter.execute-config-as-is", false)
                .putString("config_options_json", options.managedCoreOptions(Settings.configOptions, Settings.handbookRouting))
        }
        check(editor.commit()) { "Could not save regional applications" }
        return applicationSnapshot(kind)
    }
}
