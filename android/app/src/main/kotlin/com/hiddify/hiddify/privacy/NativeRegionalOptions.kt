package com.hiddify.hiddify.privacy

object NativeRoutingTokens {
    private val packagePattern = Regex("(?:[A-Za-z][A-Za-z0-9_]*\\.)+[A-Za-z][A-Za-z0-9_]*|android")
    private val domainLabel = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")

    fun merge(builtIn: List<String>, extra: String, domain: Boolean): List<String> =
        (builtIn + parse(extra, domain)).distinct().also {
            require(it.size <= 256) { "Too many routing entries" }
        }

    fun parse(text: String, domain: Boolean): List<String> = (if (domain) text.lowercase() else text)
        .split(Regex("[\\s,;]+"))
        .map { if (domain) it.trim('.').removePrefix("*.") else it }
        .filter { it.isNotBlank() }
        .map { if (domain) java.net.IDN.toASCII(it, java.net.IDN.USE_STD3_ASCII_RULES).lowercase() else it }
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
    val russianAppsBypass: Boolean = true,
    val restrictedServicesProxy: Boolean = true,
    val directDomains: String = "",
    val proxyDomains: String = "",
) {
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

    fun effectiveMode(fullTunnel: Boolean, handbookRouting: Boolean): NativeRegionalMode =
        if (fullTunnel || handbookRouting) NativeRegionalMode.OFF else mode
}
