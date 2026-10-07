package com.hiddify.hiddify.nativeprivacy

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** The snapshot field names are shared with Dart's VpnPrivacyActions. */
object NativePrivacySetup {
    const val BACKUP_KEY = "flutter.privacy-auto-setup-backup"
    const val CORE_KEY = "config_options_json"
    private const val MAX_BACKUP_LENGTH = 512 * 1024
    private val fields = linkedMapOf(
        "handbookRouting" to ("handbook-routing" to false),
        "serviceMode" to ("service-mode" to "vpn"),
        "ipv6Mode" to ("ipv6-mode" to "ipv4_only"),
        "mtu" to ("mtu" to 9000L),
        "autoReconnect" to ("auto_reconnect" to true),
        "routingMode" to ("privacy-routing-mode" to "ru-bypass"),
        "russianNetworkBypass" to ("privacy-russian-network-bypass" to true),
        "russianAppsBypass" to ("privacy-russian-apps-bypass" to true),
        "restrictedServicesProxy" to ("privacy-restricted-services-proxy" to true),
        "customDirectPackages" to ("privacy-direct-packages" to ""),
        "customProxyPackages" to ("privacy-proxy-packages" to ""),
        "customDirectDomains" to ("privacy-direct-domains" to ""),
        "customProxyDomains" to ("privacy-proxy-domains" to ""),
        "useRoot" to ("privacy-use-root" to false),
        "fullTunnel" to ("privacy-full-tunnel" to false),
        "hideLocalProxy" to ("privacy-hide-local-proxy" to true),
        "hideClashApi" to ("privacy-hide-clash-api" to true),
        "disableSystemProxy" to ("privacy-disable-system-proxy" to true),
        "publicDns" to ("privacy-public-dns" to false),
        "encryptedDns" to ("privacy-encrypted-dns" to true),
    )
    private val automatic = mapOf<String, Any>(
        "handbookRouting" to false, "serviceMode" to "vpn", "ipv6Mode" to "ipv4_only",
        "mtu" to 1400L, "autoReconnect" to true, "routingMode" to "ru-bypass",
        "russianNetworkBypass" to true, "russianAppsBypass" to true, "restrictedServicesProxy" to true,
        "hideLocalProxy" to true, "hideClashApi" to true, "disableSystemProxy" to true,
        "publicDns" to true, "encryptedDns" to true, "fullTunnel" to false,
    )

    fun canRestore(preferences: Map<String, *>): Boolean =
        (preferences[BACKUP_KEY] as? String)?.isNotBlank() == true

    fun isConfigured(preferences: Map<String, *>): Boolean {
        val snapshot = snapshot(preferences)
        return automatic.all { (field, value) -> snapshot[field] == value }
    }

    /** Returns only changed preferences. The repository commits this map in one transaction. */
    fun configure(preferences: Map<String, *>): Map<String, Any> {
        val before = snapshot(preferences)
        require(preferences[BACKUP_KEY] == null || preferences[BACKUP_KEY] is String) { "Invalid saved privacy backup" }
        val encoded = preferences[BACKUP_KEY] as? String ?: ""
        val backup = if (encoded.isBlank()) encode(before) else encoded
        require(backup.length <= MAX_BACKUP_LENGTH) { "Saved privacy settings are too large" }
        return changes(preferences, automatic) + (BACKUP_KEY to backup)
    }

    fun restore(preferences: Map<String, *>): Map<String, Any> {
        val encoded = preferences[BACKUP_KEY] as? String
        require(!encoded.isNullOrBlank()) { "No previous privacy settings saved" }
        require(encoded.length <= MAX_BACKUP_LENGTH) { "Saved privacy settings are too large" }
        val json = parseObject(encoded)
        val restored = fields.mapValues { (field, entry) ->
            val fallback = entry.second
            if (!json.has(field)) fallback else readValue(json, field, fallback)
        }
        validate(restored)
        // Clearing the backup is part of the same transaction as restoring the values.
        return changes(preferences, restored) + (BACKUP_KEY to "")
    }

    private fun snapshot(preferences: Map<String, *>): Map<String, Any> {
        val core = core(preferences)
        return fields.mapValues { (field, entry) ->
            val (key, fallback) = entry
            // Native core editors own the cached core JSON. Flutter values are used when absent.
            if ((field == "mtu" || field == "ipv6Mode") && core.has(key)) readValue(core, key, fallback)
            else preferences["flutter.$key"]?.takeIf { compatible(it, fallback) }?.let {
                if (fallback is Long) (it as Number).toLong() else it
            } ?: fallback
        }.also(::validate)
    }

    private fun changes(preferences: Map<String, *>, values: Map<String, Any>): Map<String, Any> {
        val root = core(preferences)
        val result = values.mapKeys { (field, _) -> "flutter.${fields.getValue(field).first}" }.toMutableMap()
        values["mtu"]?.let { root.addProperty("mtu", it as Long) }
        values["ipv6Mode"]?.let { root.addProperty("ipv6-mode", it as String) }
        values["serviceMode"]?.let { root.addProperty("enable-tun", it == "vpn") }
        result[CORE_KEY] = root.toString()
        return result
    }

    private fun core(preferences: Map<String, *>): JsonObject {
        val raw = preferences[CORE_KEY] as? String ?: ""
        if (raw.isBlank()) return JsonObject()
        require(raw.length <= 2 * 1024 * 1024) { "Core settings are too large" }
        return parseObject(raw)
    }

    private fun parseObject(text: String): JsonObject {
        val element = try { JsonParser.parseString(text) } catch (error: Exception) {
            throw IllegalArgumentException("Invalid saved settings", error)
        }
        require(element.isJsonObject) { "Saved settings must be a JSON object" }
        return element.asJsonObject
    }

    private fun compatible(value: Any, fallback: Any): Boolean = when (fallback) {
        is Boolean -> value is Boolean
        is String -> value is String
        is Long -> value is Long || value is Int
        else -> false
    }

    private fun readValue(json: JsonObject, key: String, fallback: Any): Any {
        val value = json.get(key)
        require(value.isJsonPrimitive) { "Invalid saved setting: $key" }
        val primitive = value.asJsonPrimitive
        return when (fallback) {
            is Boolean -> { require(primitive.isBoolean) { "Invalid saved setting: $key" }; primitive.asBoolean }
            is String -> { require(primitive.isString) { "Invalid saved setting: $key" }; primitive.asString }
            is Long -> {
                require(primitive.isNumber) { "Invalid saved setting: $key" }
                try { primitive.asBigDecimal.longValueExact() } catch (error: ArithmeticException) {
                    throw IllegalArgumentException("Invalid saved setting: $key", error)
                }
            }
            else -> error("Unsupported snapshot type")
        }
    }

    private fun validate(values: Map<String, Any>) {
        require(values["serviceMode"] in setOf("vpn", "proxy", "system-proxy")) { "Invalid saved service mode" }
        require(values["ipv6Mode"] in setOf("ipv4_only", "prefer_ipv4", "prefer_ipv6", "ipv6_only", "auto")) {
            "Invalid saved IPv6 mode"
        }
        require(values["routingMode"] in setOf("off", "ru-bypass", "proxy-selected")) { "Invalid saved routing mode" }
        require(values["mtu"] as Long in 576L..65535L) { "Invalid MTU in saved settings" }
        require(values.values.filterIsInstance<String>().all { it.length <= 65536 }) { "Saved setting is too large" }
    }

    private fun encode(values: Map<String, Any>): String = JsonObject().apply {
        values.forEach { (key, value) -> when (value) {
            is Boolean -> addProperty(key, value)
            is Number -> addProperty(key, value)
            is String -> addProperty(key, value)
        } }
    }.toString()
}
