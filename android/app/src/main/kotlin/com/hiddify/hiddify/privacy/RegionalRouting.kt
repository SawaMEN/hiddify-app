package com.hiddify.hiddify.privacy

import android.content.Context
import com.hiddify.hiddify.Settings
import org.json.JSONObject

/** Bundled catalogue is a routing suggestion, not a claim that every listed app detects VPN. */
object RegionalRouting {
    private fun tokens(text: String, domain: Boolean): List<String> = text.lowercase()
        .split(Regex("[\\s,;]+"))
        .map { if (domain) it.trim('.').removePrefix("*.") else it }
        .filter { it.isNotBlank() }.distinct().also { values ->
            require(values.size <= 256) { "Too many routing entries" }
            require(values.all { it.length <= 253 && it.matches(Regex("[a-z0-9_\\-]+(\\.[a-z0-9_\\-]+)+")) }) { "Use package names or domain names, without URLs" }
        }

    fun policy(context: Context, region: String): Map<String, Any> {
        val mode = Settings.privacyRoutingMode.takeIf { region == "ru" && !Settings.privacyFullTunnel } ?: "off"
        val catalogue = JSONObject(context.assets.open("region_routing.json").bufferedReader().use { it.readText() })
        fun list(key: String, extra: String, domain: Boolean): List<String> {
            val array = catalogue.getJSONArray(key)
            return (List(array.length()) { array.getString(it) } + tokens(extra, domain)).distinct()
        }
        val direct = list("ruPackages", Settings.privacyDirectPackages, false)
        val proxy = list("proxyPackages", Settings.privacyProxyPackages, false)
        // A direct exception wins over proxy selection, including a shared UID.
        val installedDirect = direct.filter { it != context.packageName && runCatching { context.packageManager.getApplicationInfo(it, 0) }.isSuccess }
        val directUids = installedDirect.map { context.packageManager.getApplicationInfo(it, 0).uid }.toSet()
        val installedProxy = proxy.filter { it != context.packageName && runCatching {
            context.packageManager.getApplicationInfo(it, 0).uid !in directUids
        }.getOrDefault(false) }
        return mapOf(
            "privacy-routing-mode" to mode,
            "privacy-direct-packages" to installedDirect,
            "privacy-proxy-packages" to installedProxy,
            "privacy-direct-domains" to list("ruDomains", Settings.privacyDirectDomains, true),
            "privacy-proxy-domains" to list("proxyDomains", Settings.privacyProxyDomains, true),
            "privacy-root" to Settings.privacyUseRoot,
            "privacy-root-exclude-uids" to (listOf(0, context.applicationInfo.uid) + if (mode == "ru-bypass" || mode == "proxy-selected") directUids else emptySet<Int>()),
            "privacy-root-table" to Settings.rootRouteTable,
        )
    }
}
