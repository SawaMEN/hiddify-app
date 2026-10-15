package com.hiddify.hiddify.privacy

import com.hiddify.hiddify.nativeprofile.NativeJsonDocument

/** The APK ships one catalogue schema; user application choices are stored separately. */
internal data class NativeRegionalCatalogue(
    val version: Int,
    val directPackages: List<String>,
    val directDomains: List<String>,
    val services: List<NativeRegionalService>,
) {
    companion object {
        fun parse(json: String): NativeRegionalCatalogue {
            val root = NativeJsonDocument.parse(json).asJsonObject
            val version = root.get("version").asInt
            require(version >= 5) { "Unsupported bundled routing catalogue" }
            fun tokens(keys: List<String>, domain: Boolean): List<String> = NativeRoutingTokens.merge(
                emptyList(), keys.flatMap { key ->
                    require(root.get(key)?.isJsonArray == true) { "Missing bundled routing list: $key" }
                    root.getAsJsonArray(key).map { it.asString }
                }.joinToString(","), domain)
            return NativeRegionalCatalogue(version,
                tokens(listOf("ruVpnAwarePackages", "ruCompatibilityPackages"), false),
                tokens(listOf("ruVpnAwareDomains", "ruCompatibilityDomains"), true),
                NativeRegionalServices.catalogue(root).also { require(it.isNotEmpty()) { "Service catalogue is empty" } })
        }
    }
}
