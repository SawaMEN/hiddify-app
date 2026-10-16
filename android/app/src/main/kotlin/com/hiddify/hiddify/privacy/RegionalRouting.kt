package com.hiddify.hiddify.privacy

import android.content.Context
import com.hiddify.hiddify.Settings

/** Bundled, offline routing catalogue used by the VPN privacy screen. */
object RegionalRouting {
    private fun merged(builtIn: List<String>, extra: String, domain: Boolean): List<String> =
        NativeRoutingTokens.merge(builtIn, extra, domain)

    @Volatile private var cachedCatalogue: NativeRegionalCatalogue? = null
    private fun catalogue(context: Context): NativeRegionalCatalogue = cachedCatalogue ?: synchronized(this) {
        cachedCatalogue ?: NativeRegionalCatalogue.parse(context.assets.open("region_routing.json").bufferedReader()
            .use { it.readText() }).also { cachedCatalogue = it }
    }

    fun serviceCatalogue(context: Context): List<NativeRegionalService> = catalogue(context).services

    fun policy(context: Context, options: NativeRegionalOptions? = null, packages: NativeRegionalPackageValues? = null, servicesStored: String? = null, fullTunnel: Boolean = Settings.privacyFullTunnel, handbookRouting: Boolean = Settings.handbookRouting): Map<String, Any> {
        val selected = options ?: NativeRegionalOptions(
            mode = NativeRegionalMode.fromValue(Settings.privacyRoutingMode),
            russianNetworkBypass = Settings.privacyRussianNetworkBypass,
            directDomains = Settings.privacyDirectDomains,
            proxyDomains = Settings.privacyProxyDomains,
        )
        val effective = selected.withMode(selected.effectiveMode(fullTunnel, handbookRouting))
        val mode = effective.mode.value
        val networkBypass = effective.russianNetworkBypassActive
        val catalogue = catalogue(context)
        val version = catalogue.version
        val automaticDirect = catalogue.directPackages
        val services = catalogue.services
        val selectedServices = NativeRegionalServices.selection(servicesStored ?: Settings.privacyProxyServices, services)
        val automaticProxy = NativeRegionalServices.packages(services, selectedServices)
        val directSelection = NativeRegionalPackages.selection(packages?.direct ?: Settings.privacyDirectPackages, automaticDirect)
        val proxySelection = NativeRegionalPackages.selection(packages?.proxy ?: Settings.privacyProxyPackages, automaticProxy)

        val uidCache = mutableMapOf<String, Int?>()
        fun installedUid(packageName: String): Int? {
            if (packageName == context.packageName) return null
            return uidCache.getOrPut(packageName) { runCatching { context.packageManager.getApplicationInfo(packageName, 0).uid }.getOrNull() }
        }
        fun installed(packages: List<String>): List<String> = packages.filter { installedUid(it) != null }.distinct()

        val defaultDirect = installed(automaticDirect)
        val defaultProxy = installed(automaticProxy)

        // A manually selected restricted service can override an automatic Russian-app suggestion.
        // If both sides are manual, direct routing wins while both switches are enabled.
        val directCandidates = if (proxySelection.manual && !directSelection.manual) {
            directSelection.values.filterNot { it in proxySelection.values }
        } else directSelection.values
        val configuredDirect = installed(directCandidates)
        val configuredProxy = installed(proxySelection.values)

        val effectiveDirect = if (effective.russianAppsBypass) configuredDirect else emptyList()
        val effectiveDirectWithUid = effectiveDirect.mapNotNull { packageName ->
            installedUid(packageName)?.let { uid -> packageName to uid }
        }
        val directUids = effectiveDirectWithUid.map { it.second }.toSet()
        val installedDirect = effectiveDirectWithUid.map { it.first }

        val installedProxy = if (effective.restrictedServicesProxy) {
            configuredProxy.filter { packageName ->
                installedUid(packageName)?.let { it !in directUids } == true
            }.distinct()
        } else emptyList()

        val builtInDirectDomains = if (networkBypass) {
            catalogue.directDomains
        } else emptyList()
        val directDomains = if (networkBypass || effective.mode == NativeRegionalMode.RUSSIAN_BYPASS)
            merged(builtInDirectDomains, selected.directDomains, true) else emptyList()
        val proxyDomains = if (effective.restrictedServicesProxy)
            merged(NativeRegionalServices.domains(services, selectedServices), selected.proxyDomains, true) else emptyList()

        val regionalPolicy = mapOf<String, Any>(
            // Force the generic core region internally. The old user-facing region selector is gone.
            // ru enables the existing geoip/geosite + .ru path; other disables it.
            "region" to if (networkBypass) "ru" else "other",
            "wifi-vpn-sharing" to Settings.wifiVpnSharing,
            "handbook-routing" to handbookRouting,
            "handbook-proxy" to Settings.handbookProxy,
            "handbook-direct" to Settings.handbookDirect,
            "handbook-proxy-sites" to Settings.handbookProxySites,
            "handbook-direct-sites" to Settings.handbookDirectSites,
            "privacy-routing-mode" to mode,
            "privacy-catalogue-version" to version,
            "privacy-russian-network-bypass" to networkBypass,
            "privacy-russian-apps-bypass" to effective.russianAppsBypass,
            "privacy-restricted-services-proxy" to effective.restrictedServicesProxy,
            "privacy-selected-services" to if (effective.restrictedServicesProxy)
                services.filter { it.id in selectedServices }.map { it.id } else emptyList<String>(),
            "privacy-direct-packages" to installedDirect,
            "privacy-proxy-packages" to installedProxy,
            "privacy-configured-direct-packages" to configuredDirect,
            "privacy-configured-proxy-packages" to configuredProxy,
            "privacy-default-direct-packages" to defaultDirect,
            "privacy-default-proxy-packages" to defaultProxy,
            "privacy-direct-packages-manual" to directSelection.manual,
            "privacy-proxy-packages-manual" to proxySelection.manual,
            "privacy-direct-domains" to directDomains,
            "privacy-proxy-domains" to proxyDomains,
            "privacy-root" to Settings.privacyUseRoot,
            "privacy-root-exclude-uids" to (listOf(0, context.applicationInfo.uid) + if (mode == "ru-bypass" || mode == "proxy-selected") directUids else emptySet<Int>()),
            "privacy-root-table" to Settings.rootRouteTable,
        )
        return regionalPolicy + NetworkPrivacySettings.policy(context)
    }
}
