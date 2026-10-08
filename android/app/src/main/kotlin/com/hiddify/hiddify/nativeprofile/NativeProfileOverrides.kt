package com.hiddify.hiddify.nativeprofile

import com.google.gson.JsonObject

/** Dart ProfileParser.profileOverride/applyProfileOverride, without changing global preferences. */
object NativeProfileOverrides {
    val supportedHeaders = setOf("connection-test-url", "direct-dns-address", "remote-dns-address", "tls-tricks", "chain-status", "extra-security")
    private fun objectOrEmpty(text: String?): JsonObject = runCatching {
        text?.takeIf { it.isNotBlank() }?.let { NativeJsonDocument.parse(it).asJsonObject }
    }.getOrNull() ?: JsonObject()

    fun resolve(headers: String?, userOverride: String?): JsonObject {
        val source = objectOrEmpty(headers)
        val user = objectOrEmpty(userOverride)
        val result = JsonObject()
        source.entrySet().filter { it.key in supportedHeaders && !it.value.isJsonNull }.forEach { (key, value) ->
            if (key in setOf("tls-tricks", "extra-security")) {
                val objectValue = when {
                    value.isJsonObject -> value.asJsonObject
                    value.isJsonPrimitive && value.asJsonPrimitive.isString ->
                        runCatching { NativeJsonDocument.parse(value.asString).asJsonObject }.getOrNull()
                    else -> null
                }
                objectValue?.let { result.add(key, it.deepCopy()) }
            } else if (value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString.isNotBlank()) {
                result.add(key, value.deepCopy())
            }
        }
        fun flag(name: String, header: String): Boolean {
            val value = user.get(name)
            if (value != null && value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) return value.asBoolean
            return source.get(header)?.let {
                it.isJsonPrimitive && (it.asJsonPrimitive.isBoolean || it.asJsonPrimitive.isString) &&
                    it.asString.trim().equals("true", ignoreCase = true)
            } ?: false
        }
        if (flag("enableWarp", "enable-warp") == true) {
            result.addProperty("chain-status", "extra_security")
            result.add("extra-security", JsonObject().apply { addProperty("mode", "warp") })
        }
        // Dart gives Psiphon priority when both stages were requested.
        if (flag("enablePsiphon", "enable-psiphon") == true) {
            result.addProperty("chain-status", "extra_security")
            result.add("extra-security", JsonObject().apply { addProperty("mode", "psiphon") })
        }
        val fragment = flag("enableFragment", "enable-fragment")
        if (fragment == true || user.get("enableFragment")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && !it.asBoolean } == true) {
            val tricks = result.get("tls-tricks")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
            tricks.addProperty("enable-fragment", fragment)
            result.add("tls-tricks", tricks)
        }
        return result
    }

    fun withFeatures(previous: String?, features: Set<String>?): String {
        val user = objectOrEmpty(previous)
        mapOf("enableWarp" to "warp_over_proxies", "enablePsiphon" to "psiphon_over_proxies", "enableFragment" to "fragment").forEach { (key, feature) ->
            if (features == null) user.remove(key) else user.addProperty(key, feature in features)
        }
        return user.toString()
    }

    fun apply(settings: String, headers: String?, userOverride: String?): String {
        val root = NativeJsonDocument.parse(settings.ifBlank { "{}" }).asJsonObject
        fun merge(target: JsonObject, source: JsonObject) {
            source.entrySet().forEach { (key, value) ->
                val previous = target.get(key)
                if (previous?.isJsonObject == true && value.isJsonObject) merge(previous.asJsonObject, value.asJsonObject)
                else target.add(key, value.deepCopy())
            }
        }
        merge(root, resolve(headers, userOverride))
        return root.toString()
    }
}
