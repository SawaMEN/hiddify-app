package com.hiddify.hiddify.privacy

object NativeRoutingTokens {
    private val packagePattern = Regex("(?:[A-Za-z][A-Za-z0-9_]*\\.)+[A-Za-z][A-Za-z0-9_]*|android")
    private val domainLabel = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")

    fun merge(builtIn: List<String>, extra: String, domain: Boolean): List<String> =
        (builtIn + parse(extra, domain)).distinct().also {
            require(it.size <= 256) { "Too many routing entries" }
        }

    // Domain normalization must be independent of the device locale (notably tr_TR).
    fun parse(text: String, domain: Boolean): List<String> =
        (if (domain) text.lowercase(java.util.Locale.ROOT) else text)
        .split(Regex("[\\s,;]+"))
        .map { if (domain) it.trim('.').removePrefix("*.") else it }
        .filter { it.isNotBlank() }
        .map { if (domain) java.net.IDN.toASCII(it, java.net.IDN.USE_STD3_ASCII_RULES)
            .lowercase(java.util.Locale.ROOT) else it }
        .filter { it.isNotBlank() }
        .distinct()
        .also { values ->
            require(values.size <= 256) { "Too many routing entries" }
            require(values.all {
                if (domain) it.length <= 253 && '.' in it && it.split('.').all(domainLabel::matches)
                else it.length <= 255 && packagePattern.matches(it)
            }) {
                "Use package names or domain names, without URLs"
            }
        }
}

enum class NativeRegionalMode(val value: String) {
    OFF("off"), RUSSIAN_BYPASS("ru-bypass"), SELECTED_PROXY("proxy-selected");

    companion object {
        fun fromValue(value: String): NativeRegionalMode = entries.find { it.value == value } ?: OFF
    }
}

data class NativeRegionalOptions(
    val mode: NativeRegionalMode = NativeRegionalMode.RUSSIAN_BYPASS,
    val russianNetworkBypass: Boolean = true,
    val directDomains: String = "",
    val proxyDomains: String = "",
) {
    val russianAppsBypass: Boolean get() = mode == NativeRegionalMode.RUSSIAN_BYPASS
    val restrictedServicesProxy: Boolean get() = mode != NativeRegionalMode.OFF

    fun validated(): NativeRegionalOptions {
        require(directDomains.length <= 8192 && proxyDomains.length <= 8192) { "Domain list exceeds 8192 characters" }
        fun domains(value: String): String {
            val entries = NativeRoutingTokens.parse(value, true)
            require(entries.none { Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}").matches(it) }) {
                "Use domains instead of IP addresses"
            }
            return entries.joinToString("\n")
        }
        return copy(directDomains = domains(directDomains), proxyDomains = domains(proxyDomains))
    }

    fun withMode(value: NativeRegionalMode): NativeRegionalOptions = copy(mode = value)

    fun managedCoreOptions(json: String, communityRouting: Boolean = false): String {
        if (mode == NativeRegionalMode.OFF && !russianNetworkBypass && !communityRouting) return json
        val root = com.hiddify.hiddify.nativeprofile.NativeJsonDocument.parse(json.ifBlank { "{}" }).asJsonObject.deepCopy()
        // Imported route/DNS rules must not override the explicitly selected device mode.
        root.addProperty("execute-config-as-is", false)
        root.addProperty("enable-full-config", false)
        return root.toString()
    }

    fun effectiveMode(fullTunnel: Boolean, handbookRouting: Boolean): NativeRegionalMode =
        if (fullTunnel || handbookRouting) NativeRegionalMode.OFF else mode
}
