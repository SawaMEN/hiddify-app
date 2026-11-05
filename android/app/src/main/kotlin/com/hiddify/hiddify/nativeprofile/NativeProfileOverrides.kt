package com.hiddify.hiddify.nativeprofile

import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonPrimitive

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
        val chosen = selection(headers, userOverride)
        chosen.entrySet().forEach { (key, value) -> result.add(key, value) }
        return result
    }

    /** Effective profile selection; user override has priority over subscription keep-top. */
    fun selection(headers: String?, userOverride: String?): JsonObject {
        val source = objectOrEmpty(headers)
        val user = objectOrEmpty(userOverride)
        val result = JsonObject()
        val savedTop = user.get("keepTop")?.takeIf { it.isJsonPrimitive }
        val headerTop = source.get("keep-top")?.takeIf { it.isJsonPrimitive }
        fun parseTop(value: com.google.gson.JsonElement?): Int? {
            val number = value?.let { runCatching { it.asString.trim().toIntOrNull() }.getOrNull() }
            return number?.takeIf { it == -1 || it in 1..30 }
        }
        val keepTop = parseTop(savedTop) ?: parseTop(headerTop)
        if (keepTop != null) result.addProperty("keep-top", keepTop)
        val tags = user.get("keepTags")?.takeIf { it.isJsonArray }?.asJsonArray
        if (tags != null && tags.size() in 1..512) {
            val chosen = JsonArray()
            val seen = HashSet<String>()
            for (tag in tags) {
                if (!tag.isJsonPrimitive || !tag.asJsonPrimitive.isString) continue
                val text = tag.asString
                if (text.length !in 1..255 || text.any(Char::isISOControl) || !seen.add(text)) continue
                chosen.add(text)
            }
            if (chosen.size() > 0) result.add("keep-tags", chosen)
        }
        return result
    }

    /** Keep the rest of the user override intact when changing a profile's proxy choices. */
    fun withSelection(previous: String?, keepTop: Int?, keepTags: List<String>?): String {
        require(keepTop != null || keepTags != null) { "Specify proxy selection" }
        require(keepTop == null || keepTop == -1 || keepTop in 1..30) { "Invalid keep-top" }
        require(keepTags == null || (keepTags.size <= 512 && keepTags.distinct().size == keepTags.size &&
            keepTags.all { it.length in 1..255 && it.none(Char::isISOControl) })) { "Invalid proxy tags" }
        val user = objectOrEmpty(previous).deepCopy()
        if (keepTop != null) user.addProperty("keepTop", keepTop)
        if (keepTags != null) {
            if (keepTags.isEmpty()) user.remove("keepTags")
            else user.add("keepTags", JsonArray().apply { keepTags.forEach(::add) })
        }
        return user.toString()
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
        val ownRemoteDns = root.get("remote-dns-address")?.deepCopy()
        merge(root, resolve(headers, userOverride))
        // Upstream 5.4: a subscription resolver is not necessarily reachable through
        // extra security. Apply this after merging, including profile-enabled chains.
        if (root.get("chain-status")?.asString == "extra_security") {
            if (ownRemoteDns == null) root.remove("remote-dns-address")
            else root.add("remote-dns-address", ownRemoteDns)
        }
        return root.toString()
    }
}
