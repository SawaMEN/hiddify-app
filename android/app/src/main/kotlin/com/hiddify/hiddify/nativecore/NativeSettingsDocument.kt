package com.hiddify.hiddify.nativecore

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Validate imported core options before persisting any part of a document. */
internal object NativeSettingsDocument {
    val booleanKeys = setOf("use-xray-core-when-possible", "execute-config-as-is", "resolve-destination",
        "enable-mixed-port", "enable-tproxy-port", "enable-direct-port", "enable-redirect-port",
        "strict-route", "enable-clash-api", "enable-tun", "set-system-proxy",
        "allow-connection-from-lan", "enable-fake-dns", "independent-dns-cache")
    val portKeys = setOf("mixed-port", "tproxy-port", "direct-port", "redirect-port", "clash-api-port")
    val choices = mapOf(
        "balancer-strategy" to NativeConfigChoices.balancerChoices,
        "log-level" to listOf("trace", "debug", "info", "warn", "error", "fatal", "panic"),
        "ipv6-mode" to NativeConfigChoices.domainStrategyChoices,
        "remote-dns-domain-strategy" to NativeConfigChoices.domainStrategyChoices,
        "direct-dns-domain-strategy" to NativeConfigChoices.domainStrategyChoices,
        "tun-implementation" to NativeConfigChoices.tunChoices,
        "chain-status" to listOf("off", "extra_security", "unblocker"),
    )
    val stringKeys = setOf("region", "remote-dns-address", "direct-dns-address",
        "connection-test-url", "lan-sharing-password")
    val objectKeys = setOf("tls-tricks", "extra-security", "unblocker")
    val supportedKeys = booleanKeys + portKeys + choices.keys + stringKeys + objectKeys + setOf("mtu", "url-test-interval")

    private fun text(value: JsonElement): String {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Expected string" }
        return value.asString.also { require(it.length <= 2048 && it.none(Char::isISOControl)) { "Invalid string" } }
    }
    private fun bool(value: JsonElement) {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "Expected boolean" }
    }
    private fun number(value: JsonElement, range: LongRange): Long {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "Expected integer" }
        val number = try { value.asBigDecimal.longValueExact() } catch (error: ArithmeticException) {
            throw IllegalArgumentException("Expected bounded integer", error)
        }
        require(number in range) { "Integer outside $range" }
        return number
    }

    fun merge(current: JsonObject, incoming: JsonObject): JsonObject {
        val supplied = incoming.entrySet().filter { it.key in supportedKeys }
        require(supplied.isNotEmpty()) { "No supported settings found" }
        val merged = current.deepCopy()
        fun mergeObject(target: JsonObject, source: JsonObject) {
            source.entrySet().forEach { (key, value) ->
                val old = target.get(key)
                if (old?.isJsonObject == true && value.isJsonObject) mergeObject(old.asJsonObject, value.asJsonObject)
                else target.add(key, value.deepCopy())
            }
        }
        supplied.forEach { (key, value) ->
            if (key in objectKeys) {
                require(value.isJsonObject) { "Invalid settings object: $key" }
                val target = merged.get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
                mergeObject(target, value.asJsonObject)
                merged.add(key, target)
            } else merged.add(key, value.deepCopy())
        }
        validate(merged)
        return merged
    }

    fun validate(root: JsonObject) {
        root.entrySet().filter { it.key in supportedKeys }.forEach { (key, value) ->
            try {
                when (key) {
                    in booleanKeys -> bool(value)
                    in portKeys -> number(value, 0L..65535L)
                    in choices.keys -> require(text(value) in choices.getValue(key)) { "Invalid choice" }
                    "mtu" -> number(value, 576L..65535L)
                    "url-test-interval" -> number(value, 1L..86400L)
                    "connection-test-url" -> NativeGeneralOptions(testUrl = text(value)).validated()
                    "lan-sharing-password" -> require(text(value).length <= 128) { "Password too long" }
                    "remote-dns-address", "direct-dns-address" -> require(text(value).isNotBlank()) { "Empty DNS address" }
                    "region" -> text(value)
                    "tls-tricks" -> {
                        require(value.isJsonObject) { "Expected TLS object" }
                        value.asJsonObject.entrySet().forEach { (field, setting) -> when (field) {
                            "enable-fragment", "mixed-sni-case", "enable-padding" -> bool(setting)
                            "fragment-size", "fragment-sleep", "padding-size" -> NativeTlsOptions.normalizeRange(text(setting), allowEmpty = true)
                        } }
                    }
                    "extra-security", "unblocker" -> {
                        require(value.isJsonObject) { "Expected chain object" }
                        val stage = value.asJsonObject
                        stage.get("mode")?.let { require(text(it) in listOf("warp", "psiphon", "profile")) }
                        for (type in listOf("warp", "psiphon", "profile")) stage.get(type)?.let { child ->
                            require(child.isJsonObject) { "Expected chain stage object" }
                            child.asJsonObject.entrySet().forEach { (field, setting) -> when (field) {
                                "id" -> if (!setting.isJsonNull) text(setting)
                                "clean-port" -> number(setting, 0L..65535L)
                                "license-key", "region", "conduit-pairing-id", "clean-ip", "noise-mode" -> text(setting)
                                "noise", "noise-size", "noise-delay" -> NativeTlsOptions.normalizeRange(text(setting), allowEmpty = true)
                            } }
                        }
                    }
                }
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid setting: $key", error)
            }
        }
        fun enabled(key: String, fallback: Boolean) = root.get(key)?.asBoolean ?: fallback
        fun port(key: String, fallback: Int) = root.get(key)?.asInt ?: fallback
        val listeners = mutableListOf<Int>()
        for ((key, fallback) in mapOf("mixed-port" to 12334, "direct-port" to 12337,
            "tproxy-port" to 0, "redirect-port" to 0)) {
            val port = port(key, fallback)
            if (enabled("enable-$key", fallback != 0) && port != 0) listeners += port
        }
        if (enabled("enable-clash-api", false)) listeners += port("clash-api-port", 16756)
        require(listeners.distinct().size == listeners.size) { "Enabled listeners must use different ports" }
        val mode = root.get("ipv6-mode")?.asString ?: "ipv4_only"
        require(mode in setOf("", "ipv4_only") || (root.get("mtu")?.asInt ?: 9000) >= 1280) { "IPv6 requires an MTU of at least 1280" }
    }
}
