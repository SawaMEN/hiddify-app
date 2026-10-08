package com.hiddify.hiddify.nativecore

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.hiddify.hiddify.nativeprofile.NativeJsonDocument

/** Translate the native editor schema to the options accepted by the pinned core. */
internal object NativeCoreOptionsProjection {
    fun validateChainSelection(json: String, currentProfileId: String?, availableIds: Set<String>) {
        val root = NativeJsonDocument.parse(json.ifBlank { "{}" }).asJsonObject
        val key = when (root.get("chain-status")?.asString ?: "off") {
            "off" -> return
            "extra_security" -> "extra-security"
            "unblocker" -> "unblocker"
            else -> error("Invalid chain status")
        }
        val stage = root.get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        if (stage.get("mode")?.asString != "profile") return
        val id = stage.get("profile")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("id")?.takeUnless { it.isJsonNull }?.asString
        require(!id.isNullOrBlank() && id in availableIds) { "Select an existing chain profile" }
        require(id != currentProfileId) { "The chain profile must differ from the main profile" }
    }

    fun removeDeletedChainProfile(json: String, id: String): String {
        val root = NativeJsonDocument.parse(json.ifBlank { "{}" }).asJsonObject
        var changed = false
        for ((key, status) in mapOf("extra-security" to "extra_security", "unblocker" to "unblocker")) {
            val stage = root.get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val profile = stage.get("profile")?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            if (profile.get("id")?.takeUnless { it.isJsonNull }?.asString != id) continue
            profile.add("id", com.google.gson.JsonNull.INSTANCE)
            changed = true
            if (stage.get("mode")?.asString == "profile" && root.get("chain-status")?.asString == status) {
                root.addProperty("chain-status", "off")
            }
        }
        return if (changed) root.toString() else json
    }

    fun apply(json: String, currentProfileId: String? = null, profileContent: ((String) -> String)? = null): String {
        val root = NativeJsonDocument.parse(json.ifBlank { "{}" }).asJsonObject.deepCopy()
        fun putDefault(target: JsonObject, key: String, value: Any) {
            if (target.has(key)) return
            target.add(key, when (value) {
                is Boolean -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is String -> JsonPrimitive(value)
                else -> error("Unsupported core option type")
            })
        }
        val general = NativeGeneralOptions()
        val dns = NativeDnsOptions()
        val tls = NativeTlsOptions()
        NativeGeneralOptionField.entries.forEach { putDefault(root, it.storageKey, it.value(general)) }
        NativeDnsOptionField.entries.forEach { putDefault(root, it.storageKey, it.value(dns)) }
        mapOf("mixed-port" to 12334, "direct-port" to 12337, "mtu" to 9000,
            "tun-implementation" to "gvisor", "strict-route" to true).forEach { (key, value) -> putDefault(root, key, value) }
        val tricks = root.get("tls-tricks")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        NativeTlsOptionField.entries.forEach { putDefault(tricks, it.coreKey, it.value(tls)) }
        root.add("tls-tricks", tricks)

        for (name in listOf("mixed", "direct", "tproxy", "redirect")) {
            root.get("enable-$name-port")?.let { enabled ->
                require(enabled.isJsonPrimitive && enabled.asJsonPrimitive.isBoolean) { "Invalid listener switch: $name" }
                if (!enabled.asBoolean) root.addProperty("$name-port", 0)
            }
        }
        root.get("execute-config-as-is")?.let { enabled ->
            require(enabled.isJsonPrimitive && enabled.asJsonPrimitive.isBoolean) { "Invalid raw configuration switch" }
            root.add("enable-full-config", enabled.deepCopy())
        }
        // Runtime payloads are rebuilt from the current local files, never trusted from backups.
        root.remove("chain-stage")
        val chainStatus = root.get("chain-status")?.asString ?: "off"
        if (root.has("chain-status") && chainStatus == "off") {
            for (key in listOf("warp", "warp2")) {
                root.get(key)?.takeIf { it.isJsonObject }?.asJsonObject?.addProperty("enable", false)
            }
        }
        if (chainStatus != "off") {
            val stageKey = when (chainStatus) {
                "extra_security" -> "extra-security"
                "unblocker" -> "unblocker"
                else -> error("Invalid chain status")
            }
            val stage = root.get(stageKey)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
            val mode = stage.get("mode")?.asString ?: if (stageKey == "extra-security") "warp" else "psiphon"
            require(mode in listOf("warp", "psiphon", "profile")) { "Invalid chain mode: $mode" }
            if (mode != "warp") {
                for (key in listOf("warp", "warp2")) {
                    root.get(key)?.takeIf { it.isJsonObject }?.asJsonObject?.addProperty("enable", false)
                }
                val runtime = JsonObject().apply {
                    addProperty("direction", chainStatus)
                    addProperty("mode", mode)
                }
                if (mode == "psiphon") {
                    val psiphon = stage.get("psiphon")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
                    val region = psiphon.get("region")?.asString?.uppercase(java.util.Locale.ROOT) ?: "AUTO"
                    require(region == "AUTO" || region.matches(Regex("[A-Z]{2}"))) { "Invalid Psiphon region" }
                    runtime.addProperty("region", if (region == "AUTO") "" else region)
                    runtime.addProperty("conduit-pairing-id", psiphon.get("conduit-pairing-id")?.asString?.trim() ?: "")
                } else {
                    val id = stage.get("profile")?.takeIf { it.isJsonObject }?.asJsonObject
                        ?.get("id")?.takeUnless { it.isJsonNull }?.asString
                    require(!id.isNullOrBlank() && id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Select a chain profile" }
                    require(id != currentProfileId) { "The chain profile must differ from the main profile" }
                    val content = requireNotNull(profileContent) { "Chain profile content is unavailable" }.invoke(id)
                    require(content.isNotBlank() && content.toByteArray(Charsets.UTF_8).size <= 8 * 1024 * 1024) { "Chain profile is empty or exceeds 8 MiB" }
                    runtime.addProperty("profile-content", content)
                }
                root.add("chain-stage", runtime)
                return root.toString().also {
                    require(it.toByteArray(Charsets.UTF_8).size <= 3 * 1024 * 1024) { "Chain settings exceed the local control message limit (3 MiB)" }
                }
            }
            val warp = root.get("warp")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
            stage.get("warp")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.forEach { (key, value) ->
                warp.add(if (key == "license-key") "id" else key, value.deepCopy())
            }
            warp.addProperty("enable", true)
            warp.addProperty("mode", if (chainStatus == "extra_security") "warp_over_proxy" else "proxy_over_warp")
            root.add("warp", warp)
        }
        return root.toString()
    }

    /** Import validates the source itself; selecting a chain is a separate runtime operation. */
    fun forProfileValidation(json: String): String {
        val root = NativeJsonDocument.parse(json).asJsonObject.deepCopy()
        root.addProperty("chain-status", "off")
        return apply(root.toString())
    }
}
