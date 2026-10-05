package com.hiddify.hiddify.privacy

import android.content.Context
import com.hiddify.hiddify.Settings
import org.json.JSONObject

/** Bundled, offline routing catalogue used by the VPN privacy screen. */
object RegionalRouting {
    private const val MANUAL_PREFIX = "manual:"
    private val tokenPattern = Regex("[a-z0-9_\\-]+(\\.[a-z0-9_\\-]+)+")

    private fun tokens(text: String, domain: Boolean): List<String> = text.lowercase()
        .split(Regex("[\\s,;]+"))
        .map { if (domain) it.trim('.').removePrefix("*.") else it }
        .filter { it.isNotBlank() }
        .distinct()
        .also { values ->
            require(values.size <= 256) { "Too many routing entries" }
            require(values.all { it.length <= 253 && tokenPattern.matches(it) }) {
                "Use package names or domain names, without URLs"
            }
        }

    private fun catalogueTokens(catalogue: JSONObject, keys: List<String>, domain: Boolean): List<String> {
        val result = keys.flatMap { key ->
            val array = catalogue.optJSONArray(key) ?: return@flatMap emptyList<String>()
            tokens(List(array.length()) { array.getString(it) }.joinToString(","), domain)
        }.distinct()
        require(result.size <= 256) { "Too many bundled routing entries" }
        return result
    }

    private fun merged(builtIn: List<String>, extra: String, domain: Boolean): List<String> =
        (builtIn + tokens(extra, domain)).distinct().also {
            require(it.size <= 256) { "Too many routing entries" }
        }

    private data class PackageSelection(val manual: Boolean, val values: List<String>)

    private fun packageSelection(stored: String, automatic: List<String>): PackageSelection {
        if (stored.startsWith(MANUAL_PREFIX)) {
            return PackageSelection(true, tokens(stored.removePrefix(MANUAL_PREFIX), false))
        }
        // Plain values are kept as a migration path for older custom-package settings.
        return PackageSelection(false, merged(automatic, stored, false))
    }

    fun policy(context: Context, region: String): Map<String, Any> {
        val mode = Settings.privacyRoutingMode.takeIf { region == "ru" && !Settings.privacyFullTunnel } ?: "off"
        val catalogue = JSONObject(context.assets.open("region_routing.json").bufferedReader().use { it.readText() })
        val version = catalogue.optInt("version", 1)

        val vpnAwarePackages = if (catalogue.has("ruVpnAwarePackages")) {
            catalogueTokens(catalogue, listOf("ruVpnAwarePackages"), false)
        } else emptyList()
        val compatibilityPackages = if (catalogue.has("ruCompatibilityPackages")) {
            catalogueTokens(catalogue, listOf("ruCompatibilityPackages"), false)
        } else catalogueTokens(catalogue, listOf("ruPackages"), false)
        val vpnAwareDomains = if (catalogue.has("ruVpnAwareDomains")) {
            catalogueTokens(catalogue, listOf("ruVpnAwareDomains"), true)
        } else emptyList()
        val compatibilityDomains = if (catalogue.has("ruCompatibilityDomains")) {
            catalogueTokens(catalogue, listOf("ruCompatibilityDomains"), true)
        } else catalogueTokens(catalogue, listOf("ruDomains"), true)

        val automaticDirect = (vpnAwarePackages + compatibilityPackages).distinct()
        val automaticProxy = catalogueTokens(catalogue, listOf("proxyPackages"), false)
        val directSelection = packageSelection(Settings.privacyDirectPackages, automaticDirect)
        val proxySelection = packageSelection(Settings.privacyProxyPackages, automaticProxy)

        fun installedUid(packageName: String): Int? {
            if (packageName == context.packageName) return null
            return runCatching { context.packageManager.getApplicationInfo(packageName, 0).uid }.getOrNull()
        }
        fun installed(packages: List<String>): List<String> = packages.filter { installedUid(it) != null }.distinct()

        val defaultDirect = installed(automaticDirect)
        val defaultProxy = installed(automaticProxy)

        // An explicit restricted-service choice must be able to override an automatic direct
        // suggestion. When both sides are manually edited the direct side still wins on shared UIDs.
        val directCandidates = if (proxySelection.manual && !directSelection.manual) {
            directSelection.values.filterNot { it in proxySelection.values }
        } else directSelection.values

        val installedDirectWithUid = directCandidates.mapNotNull { packageName ->
            installedUid(packageName)?.let { uid -> packageName to uid }
        }
        val installedDirect = installedDirectWithUid.map { it.first }
        val directUids = installedDirectWithUid.map { it.second }.toSet()
        val installedProxy = proxySelection.values.filter { packageName ->
            installedUid(packageName)?.let { it !in directUids } == true
        }.distinct()

        val directDomains = merged((vpnAwareDomains + compatibilityDomains).distinct(), Settings.privacyDirectDomains, true)
        val proxyDomains = merged(catalogueTokens(catalogue, listOf("proxyDomains"), true), Settings.privacyProxyDomains, true)

        val regionalPolicy = mapOf<String, Any>(
            "privacy-routing-mode" to mode,
            "privacy-catalogue-version" to version,
            "privacy-direct-packages" to installedDirect,
            "privacy-proxy-packages" to installedProxy,
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
