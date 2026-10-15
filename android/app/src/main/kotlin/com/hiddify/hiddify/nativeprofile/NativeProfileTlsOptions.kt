package com.hiddify.hiddify.nativeprofile

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.net.IDN
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

/** Rebuild a runtime copy; refreshing a subscription never overwrites its local TLS choices. */
data class NativeProfileTlsOptions(
    val serverName: String = "",
    val fingerprint: String = "",
    val alpn: String = "",
    val fragmentMode: String = "",
    val fallbackDelayMs: String = "",
    val minVersion: String = "",
    val maxVersion: String = "",
    val targetTag: String = "",
    val sniMode: String = "",
) {
    val enabled: Boolean get() = serverName.isNotBlank() || fingerprint.isNotBlank() || alpn.isNotBlank() ||
        fragmentMode.isNotBlank() || fallbackDelayMs.isNotBlank() || minVersion.isNotBlank() || maxVersion.isNotBlank() || sniMode.isNotBlank()

    fun validated(): NativeProfileTlsOptions {
        val sni = serverName.trim().let { if (it.isEmpty()) it else IDN.toASCII(it) }
        require(sni.isEmpty() || (sni.length <= 253 && sni.split('.').all {
            it.length in 1..63 && it.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"))
        })) { "Invalid TLS server name" }
        require(fingerprint in fingerprints && fragmentMode in fragmentModes && sniMode in listOf("", "on", "off")) { "Invalid TLS choice" }
        val protocols = alpn.split(',').map(String::trim).filter(String::isNotEmpty)
        require(protocols.size <= 16 && protocols.all { it.length in 1..255 && it.all { c -> c.code in 33..126 && c != ',' } }) { "Invalid ALPN list" }
        require(fallbackDelayMs.isEmpty() || fallbackDelayMs.toIntOrNull()?.let { it in 1..10000 } == true) { "TLS delay must be 1–10000 ms" }
        require(minVersion in versions && maxVersion in versions && (minVersion.isEmpty() || maxVersion.isEmpty() || minVersion <= maxVersion)) { "Invalid TLS version range" }
        require(targetTag.length <= 1024 && targetTag.none(Char::isISOControl)) { "Invalid TLS target tag" }
        return copy(serverName = sni, alpn = protocols.distinct().joinToString(","), targetTag = targetTag.trim())
    }

    fun toJson() = JsonObject().apply {
        addProperty("server-name", serverName); addProperty("fingerprint", fingerprint); addProperty("alpn", alpn)
        addProperty("fragment-mode", fragmentMode); addProperty("fallback-delay-ms", fallbackDelayMs)
        addProperty("min-version", minVersion); addProperty("max-version", maxVersion); addProperty("target-tag", targetTag); addProperty("sni-mode", sniMode)
    }

    fun apply(content: String): String {
        if (!enabled) return content
        val value = validated()
        val decoded = NativeSubscriptionContent.decode(content)
        var changed = 0
        fun matches(tag: String) = value.targetTag.isEmpty() || tag == value.targetTag
        fun patchOutbound(outbound: JsonObject) {
            if (!matches(outbound.get("tag")?.asString.orEmpty())) return
            val type = outbound.get("type")?.asString ?: return
            if (type !in tlsTypes) return
            val tls = outbound.get("tls")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
            if (tls.get("enabled")?.asBoolean != true) return
            val reality = tls.get("reality")?.takeIf { it.isJsonObject }?.asJsonObject?.get("enabled")?.asBoolean == true
            val tcp = type in tcpTypes && !reality
            var applied = false
            fun text(key: String, item: String) { if (item.isNotEmpty()) { tls.addProperty(key, item); applied = true } }
            text("server_name", value.serverName)
            if (value.serverName.isNotEmpty()) tls.addProperty("disable_sni", false)
            if (tcp && value.sniMode.isNotEmpty()) { tls.addProperty("disable_sni", value.sniMode == "off"); applied = true }
            if (value.fingerprint.isNotEmpty() && type in tcpTypes && !(reality && value.fingerprint == "off")) {
                val utls = tls.get("utls")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
                utls.addProperty("enabled", value.fingerprint != "off")
                if (value.fingerprint == "off") utls.remove("fingerprint") else utls.addProperty("fingerprint", value.fingerprint)
                tls.add("utls", utls); applied = true
            }
            if (value.alpn.isNotEmpty()) {
                tls.add("alpn", JsonArray().apply { value.alpn.split(',').forEach(::add) }); applied = true
            }
            text("min_version", value.minVersion); text("max_version", value.maxVersion)
            if ((tcp || value.fragmentMode == "off" && type in tcpTypes) && value.fragmentMode.isNotEmpty()) {
                tls.addProperty("fragment", value.fragmentMode in listOf("tcp", "both"))
                tls.addProperty("record_fragment", value.fragmentMode in listOf("record", "both")); applied = true
            }
            if (tcp && value.fallbackDelayMs.isNotEmpty()) { tls.addProperty("fragment_fallback_delay", "${value.fallbackDelayMs}ms"); applied = true }
            if (applied) changed++
        }
        fun walk(element: JsonElement) {
            when {
                element.isJsonArray -> element.asJsonArray.forEach(::walk)
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    if (obj.has("outbounds")) walk(obj.get("outbounds")) else patchOutbound(obj)
                }
            }
        }
        val output = if (decoded.trimStart().startsWith('{') || decoded.trimStart().startsWith('[')) {
            NativeJsonDocument.parse(decoded).also(::walk).toString()
        } else {
            require(value.sniMode.isEmpty() && value.fingerprint != "off" && value.fallbackDelayMs.isEmpty() && value.minVersion.isEmpty() && value.maxVersion.isEmpty() && value.fragmentMode !in listOf("record", "both")) {
                "These advanced TLS settings require a sing-box JSON profile / Нужен профиль JSON sing-box"
            }
            decoded.lines().joinToString("\n") { original ->
                val line = original.trim()
                if (line.startsWith("vmess://", true)) {
                    val encoded = line.substringAfter("://").replace('-', '+').replace('_', '/')
                    val vmess = NativeJsonDocument.parse(NativeSubscriptionContent.utf8(Base64.getDecoder().decode(encoded))).asJsonObject
                    if (vmess.get("tls")?.asString == "tls" && matches(vmess.get("ps")?.asString.orEmpty())) {
                        if (value.serverName.isNotEmpty()) vmess.addProperty("sni", value.serverName)
                        if (value.fingerprint.isNotEmpty()) vmess.addProperty("fp", value.fingerprint)
                        if (value.alpn.isNotEmpty()) vmess.addProperty("alpn", value.alpn)
                        if (value.fragmentMode.isNotEmpty()) {
                            listOf("fragment", "fgsize", "fgsleep").forEach(vmess::remove)
                            if (value.fragmentMode == "tcp") vmess.addProperty("fragment", "tlshello,10-30,2-8")
                        }
                        changed++
                        "vmess://" + Base64.getEncoder().encodeToString(vmess.toString().toByteArray(Charsets.UTF_8))
                    } else original
                } else if (line.startsWith("vless://", true) || line.startsWith("trojan://", true)) {
                    val uri = URI(line)
                    val query = uri.rawQuery.orEmpty().split('&').filter(String::isNotEmpty)
                    fun key(part: String) = URLDecoder.decode(part.substringBefore('='), "UTF-8").lowercase(java.util.Locale.ROOT).replace("_", "").replace("-", "")
                    val params = query.associate { key(it) to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }
                    val security = params["security"]?.takeIf { it in listOf("tls", "reality") } ?: params["tls"]
                    val name = uri.rawFragment?.let(::decode).orEmpty()
                    if (security in listOf("tls", "reality") && matches(name)) {
                        val edits = linkedMapOf<String, String>()
                        if (value.serverName.isNotEmpty()) edits["sni"] = value.serverName
                        if (value.fingerprint.isNotEmpty()) edits["fp"] = value.fingerprint
                        if (value.alpn.isNotEmpty()) edits["alpn"] = value.alpn
                        val removed = edits.keys.toMutableSet()
                        if (value.serverName.isNotEmpty()) removed.addAll(listOf("peer", "nosni"))
                        if (value.fragmentMode.isNotEmpty() && (security != "reality" || value.fragmentMode == "off")) {
                            removed.addAll(listOf("fragment", "fgsize", "fgsleep"))
                            if (value.fragmentMode == "tcp") edits["fragment"] = "tlshello,10-30,2-8"
                        }
                        if (edits.isEmpty() && removed.isEmpty()) original else {
                            val updatedQuery = (query.filter { key(it) !in removed } + edits.map { (k, v) -> "$k=${encode(v)}" }).joinToString("&")
                            changed++
                            line.substringBefore('#').substringBefore('?') + (if (updatedQuery.isEmpty()) "" else "?$updatedQuery") +
                                (uri.rawFragment?.let { "#$it" } ?: "")
                        }
                    } else original
                } else original
            }
        }
        require(changed > 0) { "No compatible TLS servers match these overrides / Нет подходящих TLS-серверов" }
        require(output.toByteArray(Charsets.UTF_8).size <= NativeProfileTransfer.MAX_CONFIG_BYTES) { "TLS configuration exceeds 8 MiB" }
        return output
    }

    companion object {
        val fingerprints = listOf("", "off", "chrome", "firefox", "edge", "safari", "ios", "android", "360", "qq", "random", "randomized")
        val fragmentModes = listOf("", "off", "tcp", "record", "both")
        val versions = listOf("", "1.2", "1.3")
        private val tcpTypes = setOf("vless", "vmess", "trojan", "http", "socks", "anytls", "shadowtls")
        private val tlsTypes = tcpTypes + setOf("hysteria", "hysteria2", "tuic")
        private fun decode(text: String) = URLDecoder.decode(text.replace("+", "%2B"), "UTF-8")
        private fun encode(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
        fun fromJson(obj: JsonObject?): NativeProfileTlsOptions {
            fun text(key: String) = obj?.get(key)?.asString.orEmpty()
            return NativeProfileTlsOptions(text("server-name"), text("fingerprint"), text("alpn"), text("fragment-mode"),
                text("fallback-delay-ms"), text("min-version"), text("max-version"), text("target-tag"), text("sni-mode"))
        }
    }
}
