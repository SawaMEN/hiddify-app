package com.hiddify.hiddify.nativecore

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.hiddify.hiddify.nativeprofile.NativeJsonDocument

/** Translate the native editor schema to the options accepted by the pinned core. */
internal object NativeCoreOptionsProjection {
    fun apply(json: String): String {
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
        // This revision accepts warp/warp2, rather than the newer Dart chain schema.
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
            // Do not report a protected connection when the core silently ignores this stage.
            require(mode == "warp") { "The bundled core cannot apply a $mode chain stage; disable the chain or use a profile containing that outbound" }
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
}
