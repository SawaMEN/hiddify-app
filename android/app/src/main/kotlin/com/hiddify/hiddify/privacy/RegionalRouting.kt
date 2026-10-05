package com.hiddify.hiddify.privacy

import android.content.Context
import com.hiddify.hiddify.Settings
import org.json.JSONObject

/**
 * Bundled routing catalogue.
 *
 * `ruVpnAware*` entries follow the published 16 April 2026 repeat APK analysis and
 * device testing. Domain entries are first-party routing targets associated with those
 * apps; they are not a claim that every individual hostname performs VPN detection.
 * Compatibility entries are intentionally kept separate from VPN-aware entries.
 */
object RegionalRouting {
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

    fun policy(context: Context, region: String): Map<String, Any> {
        val mode = Settings.privacyRoutingMode.takeIf { region == "ru" && !Settings.privacyFullTunnel } ?: "off"
        val catalogue = JSONObject(context.assets.open("region_routing.json").bufferedReader().use { it.readText() })
        val version = catalogue.optInt("version", 1)

        // Keep compatibility with an older bundled catalogue so a partial/rollback install does
        // not turn routing into an empty policy.
        val vpnAwarePackages = if (catalogue.has("ruVpnAwarePackages")) {
            catalogueTokens(catalogue, listOf("ruVpnAwarePackages"), false)
        } else {
            emptyList()
        }
        val compatibilityPackages = if (catalogue.has("ruCompatibilityPackages")) {
            catalogueTokens(catalogue, listOf("ruCompatibilityPackages"), false)
        } else {
            catalogueTokens(catalogue, listOf("ruPackages"), false)
        }
        val vpnAwareDomains = if (catalogue.has("ruVpnAwareDomains")) {
            catalogueTokens(catalogue, listOf("ruVpnAwareDomains"), true)
        } else {
            emptyList()
        }
        val compatibilityDomains = if (catalogue.has("ruCompatibilityDomains")) {
            catalogueTokens(catalogue, listOf("ruCompatibilityDomains"), true)
        } else {
            catalogueTokens(catalogue, listOf("ruDomains"), true)
        }

        val direct = merged((vpnAwarePackages + compatibilityPackages).distinct(), Settings.privacyDirectPackages, false)
        val proxy = merged(catalogueTokens(catalogue, listOf("proxyPackages"), false), Settings.privacyProxyPackages, false)
        val directDomains = merged((vpnAwareDomains + compatibilityDomains).distinct(), Settings.privacyDirectDomains, true)
        val proxyDomains = merged(catalogueTokens(catalogue, listOf("proxyDomains"), true), Settings.privacyProxyDomains, true)

        fun installedUid(packageName: String): Int? {
            if (packageName == context.packageName) return null
            return runCatching { context.packageManager.getApplicationInfo(packageName, 0).uid }.getOrNull()
        }

        val installedDirectWithUid = direct.mapNotNull { packageName ->
            installedUid(packageName)?.let { uid -> packageName to uid }
        }
        val installedDirect = installedDirectWithUid.map { it.first }
        val directUids = installedDirectWithUid.map { it.second }.toSet()
        val installedVpnAware = vpnAwarePackages.filter { it in installedDirect }

        // A direct exception wins over proxy selection, including apps that share a UID.
        val installedProxy = proxy.filter { packageName ->
            installedUid(packageName)?.let { it !in directUids } == true
        }

        val regionalPolicy = mapOf<String, Any>(
            "privacy-routing-mode" to mode,
            "privacy-catalogue-version" to version,
            "privacy-vpn-aware-packages" to installedVpnAware,
            "privacy-direct-packages" to installedDirect,
            "privacy-proxy-packages" to installedProxy,
            "privacy-direct-domains" to directDomains,
            "privacy-proxy-domains" to proxyDomains,
            "privacy-root" to Settings.privacyUseRoot,
            "privacy-root-exclude-uids" to (listOf(0, context.applicationInfo.uid) + if (mode == "ru-bypass" || mode == "proxy-selected") directUids else emptySet<Int>()),
            "privacy-root-table" to Settings.rootRouteTable,
        )
        return regionalPolicy + NetworkPrivacySettings.policy(context)
    }
}
