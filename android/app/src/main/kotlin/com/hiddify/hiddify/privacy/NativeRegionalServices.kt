package com.hiddify.hiddify.privacy

import com.hiddify.hiddify.nativeprofile.NativeJsonDocument

data class NativeRegionalService(val id: String, val name: String, val packages: List<String>, val domains: List<String>)
data class NativeRegionalAppChoices(val packages: Set<String>? = null, val services: Set<String>? = null, val reset: Boolean = false,
    val russianNetworkBypass: Boolean? = null, val domains: String? = null)

/** Offline service choices drive both browser-domain rules and automatic app suggestions. */
object NativeRegionalServices {
    private val idPattern = Regex("[a-z][a-z0-9-]{0,63}")

    fun catalogue(json: String): List<NativeRegionalService> {
        val root = NativeJsonDocument.parse(json).asJsonObject
        val rows = root.getAsJsonArray("proxyServices") ?: return emptyList()
        require(rows.size() <= 64) { "Too many services" }
        return rows.map { row ->
            val value = row.asJsonObject
            val id = value.get("id").asString
            val name = value.get("name").asString
            require(idPattern.matches(id) && name.isNotBlank() && name.length <= 128) { "Invalid service" }
            fun tokens(key: String, domain: Boolean): List<String> = NativeRoutingTokens.parse(
                value.getAsJsonArray(key).joinToString(",") { it.asString }, domain,
            )
            NativeRegionalService(id, name, tokens("packages", false), tokens("domains", true))
        }.also { services ->
            require(services.map { it.id }.distinct().size == services.size) { "Duplicate service" }
            require(services.flatMap { it.domains }.distinct().size <= 256) { "Too many service domains" }
            require(services.flatMap { it.packages }.distinct().size <= 256) { "Too many service apps" }
        }
    }

    fun selection(stored: String, catalogue: List<NativeRegionalService>): Set<String> {
        require(stored.length <= 8192) { "Service selection is too large" }
        if (stored.isBlank()) return catalogue.map { it.id }.toSet()
        require(stored.startsWith(NativeRegionalPackages.MANUAL_PREFIX)) { "Invalid service selection" }
        val ids = stored.removePrefix(NativeRegionalPackages.MANUAL_PREFIX).split(',').filter { it.isNotBlank() }.toSet()
        validate(ids)
        return ids
    }

    fun encode(ids: Set<String>): String {
        validate(ids)
        return NativeRegionalPackages.MANUAL_PREFIX + ids.sorted().joinToString(",")
    }

    fun packages(catalogue: List<NativeRegionalService>, selected: Set<String>): List<String> =
        catalogue.filter { it.id in selected }.flatMap { it.packages }.distinct()

    fun domains(catalogue: List<NativeRegionalService>, selected: Set<String>): List<String> =
        catalogue.filter { it.id in selected }.flatMap { it.domains }.distinct()

    fun changes(current: NativeRegionalPackageValues, currentServices: String, kind: NativeRegionalAppKind,
        choices: NativeRegionalAppChoices): Map<String, String> {
        val next = when {
            choices.reset -> NativeRegionalPackages.reset(current, kind)
            choices.packages != null -> NativeRegionalPackages.replace(current, kind, choices.packages)
            else -> current
        }
        val changes = linkedMapOf("flutter.privacy-direct-packages" to next.direct,
            "flutter.privacy-proxy-packages" to next.proxy)
        if (kind == NativeRegionalAppKind.PROXY) changes["flutter.privacy-proxy-services"] =
            if (choices.reset) "" else choices.services?.let(::encode) ?: currentServices
        return changes
    }

    /** Domain/network edits are atomic with app choices and do not select a routing mode. */
    fun options(current: NativeRegionalOptions, kind: NativeRegionalAppKind, choices: NativeRegionalAppChoices): NativeRegionalOptions {
        require(kind == NativeRegionalAppKind.DIRECT || choices.russianNetworkBypass == null) {
            "Russian network settings belong to direct routing"
        }
        return (if (kind == NativeRegionalAppKind.DIRECT) current.copy(
            russianNetworkBypass = choices.russianNetworkBypass ?: current.russianNetworkBypass,
            directDomains = choices.domains ?: current.directDomains,
        ) else current.copy(proxyDomains = choices.domains ?: current.proxyDomains)).validated()
    }

    private fun validate(ids: Set<String>) {
        require(ids.size <= 64 && ids.all(idPattern::matches)) { "Invalid service selection" }
    }
}
