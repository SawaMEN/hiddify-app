package com.hiddify.hiddify.nativediagnostics

import com.hiddify.hiddify.nativecore.NativeOutbound
import com.hiddify.hiddify.nativeprofile.NativeJsonDocument
import com.google.gson.JsonElement
import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import com.hiddify.hiddify.nativeprofile.NativeSubscriptionContent

internal data class NativeDiagnosticEndpoint(val tag: String, val type: String, val host: String, val port: Int) {
    val udp: Boolean get() = type in setOf("tuic", "hysteria", "hy", "hysteria2", "hy2", "wireguard",
        "wireguard_legacy", "hysteria2_legacy", "wg", "awg", "masque", "masque-client",
        "naive+quic", "quic", "http3")
}

/** ImportSummary's endpoint selection. Only the outcome is allowed into a report. */
internal object NativeDiagnosticEndpoints {
    private val utility = setOf("direct", "block", "dns", "selector", "urltest", "url-test", "reject")
    fun parse(raw: String): List<NativeDiagnosticEndpoint> {
        val content = NativeSubscriptionContent.decode(raw).trim()
        val json = runCatching { NativeJsonDocument.parse(content) }.getOrNull()
        if (json != null && (json.isJsonObject || json.isJsonArray)) {
            val configurations = mutableListOf<com.google.gson.JsonObject>()
            fun collect(value: JsonElement) {
                if (value.isJsonArray) value.asJsonArray.forEach(::collect)
                else if (value.isJsonObject) configurations += value.asJsonObject
            }
            collect(json)
            return configurations.flatMap { configuration -> listOf("outbounds", "endpoints").flatMap { key ->
                val array = configuration.get(key)?.takeIf { it.isJsonArray }?.asJsonArray
                array?.mapNotNull { item ->
                    if (!item.isJsonObject) return@mapNotNull null
                    val obj = item.asJsonObject
                    fun text(key: String) = obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                    val type = text("type").ifBlank { text("protocol").ifBlank { "unknown" } }.lowercase()
                    if (type in utility) return@mapNotNull null
                    var host = text("server").ifBlank { text("address") }
                    var port = text("server_port").ifBlank { text("port") }.toIntOrNull() ?: 0
                    val settings = obj.get("settings")?.takeIf { it.isJsonObject }?.asJsonObject
                    val next = (settings?.get("vnext") ?: settings?.get("servers"))?.takeIf { it.isJsonArray }?.asJsonArray
                    next?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject?.let {
                        host = it.get("address")?.takeIf { v -> v.isJsonPrimitive }?.asString ?: host
                        port = it.get("port")?.takeIf { v -> v.isJsonPrimitive }?.asString?.toIntOrNull() ?: port
                    }
                    NativeDiagnosticEndpoint(text("tag").ifBlank { text("name").ifBlank { type } }, type, host, port)
                }.orEmpty()
            } }
        }
        val clash = parseClashYaml(content)
        if (clash.isNotEmpty()) return clash
        return content.lineSequence().mapNotNull { line ->
            val text = line.trim()
            val uri = runCatching { URI(text) }.getOrNull() ?: return@mapNotNull null
            val type = uri.scheme?.lowercase() ?: return@mapNotNull null
            if (!text.contains("://") || type in utility || type in setOf("http", "https", "hiddify")) return@mapNotNull null
            var host = uri.host.orEmpty()
            var port = uri.port.coerceAtLeast(0)
            var tag = uri.rawFragment?.let { runCatching { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.getOrDefault(it) }.orEmpty()
            if (type == "ss" && uri.rawUserInfo == null && uri.port == -1) runCatching {
                val encoded = text.substringAfter("://").substringBefore('#').trimEnd('/').replace('-', '+').replace('_', '/')
                val decoded = URI("ss://" + String(Base64.getDecoder().decode(encoded), Charsets.UTF_8))
                host = decoded.host.orEmpty()
                port = decoded.port.coerceAtLeast(0)
            }
            if (type == "vmess") runCatching {
                val encoded = text.substring(8).substringBefore('#').replace('-', '+').replace('_', '/')
                val obj = NativeJsonDocument.parse(String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)).asJsonObject
                host = obj.get("add")?.asString.orEmpty()
                port = obj.get("port")?.asString?.toIntOrNull() ?: 0
                tag = obj.get("ps")?.asString ?: tag
            }
            NativeDiagnosticEndpoint(tag.ifBlank { type }, type, host, port)
        }.toList()
    }
    /**
     * Parse only simple top-level Clash proxy entries. Never turn a nested header,
     * proxy-provider URL, or a remote DNS address into a TCP destination.
     * Complex YAML is resolved from the running core, not guessed from text.
     */
    private fun parseClashYaml(content: String): List<NativeDiagnosticEndpoint> {
        val listHeader = Regex("""^\s*proxies\s*:\s*(?:#.*)?$""")
        val rootKey = Regex("""^[A-Za-z][A-Za-z0-9_-]*\s*:""")
        val itemStart = Regex("""^(\s*)-\s+([A-Za-z][A-Za-z0-9_-]*)\s*:\s*(.*?)\s*$""")
        val field = Regex("""^(\s+)([A-Za-z][A-Za-z0-9_-]*)\s*:\s*(.*?)\s*$""")
        val result = mutableListOf<NativeDiagnosticEndpoint>()
        var inProxies = false
        var itemIndent: Int? = null
        var current: MutableMap<String, String>? = null
        fun unquote(text: String): String {
            val trimmed = text.trim().substringBefore(" #").trim()
            return if (trimmed.length >= 2 &&
                ((trimmed.first() == '"' && trimmed.last() == '"') ||
                    (trimmed.first() == '\'' && trimmed.last() == '\'')))
                trimmed.substring(1, trimmed.lastIndex)
            else trimmed
        }
        fun commit() {
            val fields = current ?: return
            val type = fields["type"]?.lowercase().orEmpty()
            if (type.isBlank() || type in utility) return
            result += NativeDiagnosticEndpoint(
                fields["name"].orEmpty().ifBlank { type }, type,
                fields["server"].orEmpty(),
                fields["port"]?.toIntOrNull() ?: 0,
            )
        }
        for (line in content.lineSequence()) {
            if (!inProxies) {
                if (listHeader.matches(line)) inProxies = true
                continue
            }
            if (rootKey.containsMatchIn(line)) break
            val item = itemStart.matchEntire(line)
            if (item != null) {
                val indent = item.groupValues[1].length
                if (itemIndent == null) itemIndent = indent
                if (itemIndent == indent) {
                    commit()
                    if (result.size >= 2048) break
                    current = mutableMapOf(item.groupValues[2] to unquote(item.groupValues[3]))
                    continue
                }
            }
            val property = field.matchEntire(line) ?: continue
            // Properties of nested 'ws-opts', 'headers', etc. must not override
            // the server and port of the actual proxy entry.
            if (current != null && itemIndent != null &&
                property.groupValues[1].length == itemIndent + 2) {
                val key = property.groupValues[2]
                if (key in setOf("name", "type", "server", "port")) {
                    current[key] = unquote(property.groupValues[3])
                }
            }
        }
        commit()
        return result.take(2048)
    }

    /** Prefer the core's effective server if it provides a concrete remote endpoint.
     * This fixes group tags and Clash subscriptions whose names do not match core tags.
     * A selector or other group is not a TCP endpoint, even if it has a display name.
     */
    fun resolve(endpoints: List<NativeDiagnosticEndpoint>, tag: String?, live: NativeOutbound?): NativeDiagnosticEndpoint? {
        if (live != null && !live.isGroup &&
            live.type.lowercase() !in utility &&
            live.host.isNotBlank() && live.port in 1..65535) {
            return NativeDiagnosticEndpoint(live.tag, live.type.lowercase(), live.host, live.port)
        }
        return selected(endpoints, tag)
    }

    fun selected(endpoints: List<NativeDiagnosticEndpoint>, tag: String?): NativeDiagnosticEndpoint? {
        val matches = endpoints.filter { it.tag == tag }
        return if (matches.size == 1) matches.single() else endpoints.singleOrNull()
    }
    fun chained(raw: String, headers: Set<String>): Boolean {
        if (headers.any { it.lowercase() in setOf("chain-status", "enable-chain", "enable-warp", "enable-psiphon") }) return true
        fun hasDetour(value: JsonElement): Boolean = when {
            value.isJsonObject -> value.asJsonObject.entrySet().any { (key, child) ->
                (key in setOf("detour", "dialerProxy") && child.isJsonPrimitive &&
                    child.asJsonPrimitive.isString && child.asString.isNotBlank()) || hasDetour(child)
            }
            value.isJsonArray -> value.asJsonArray.any(::hasDetour)
            else -> false
        }
        val json = runCatching { NativeJsonDocument.parse(NativeSubscriptionContent.decode(raw)) }.getOrNull() ?: return false
        return hasDetour(json)
    }
}
