package com.hiddify.hiddify.nativediagnostics

import com.hiddify.hiddify.nativeprofile.NativeJsonDocument
import com.google.gson.JsonElement
import java.net.URI
import java.net.URLDecoder
import java.util.Base64

internal data class NativeDiagnosticEndpoint(val tag: String, val type: String, val host: String, val port: Int) {
    val udp: Boolean get() = type in setOf("tuic", "hysteria", "hy", "hysteria2", "hy2", "wireguard",
        "wireguard_legacy", "hysteria2_legacy", "wg", "awg", "masque", "masque-client")
}

/** ImportSummary's endpoint selection. Only the outcome is allowed into a report. */
internal object NativeDiagnosticEndpoints {
    private val utility = setOf("direct", "block", "dns", "selector", "urltest", "url-test", "reject")
    fun parse(raw: String): List<NativeDiagnosticEndpoint> {
        var content = raw.trim()
        if (!content.contains("://") && !content.startsWith("{") && !content.contains(':')) {
            content = runCatching { String(Base64.getDecoder().decode(content.filterNot(Char::isWhitespace)
                .replace('-', '+').replace('_', '/')), Charsets.UTF_8) }.getOrDefault(content)
        }
        val json = runCatching { NativeJsonDocument.parse(content) }.getOrNull()
        if (json?.isJsonObject == true) {
            return listOf("outbounds", "endpoints").flatMap { key ->
                val array = json.asJsonObject.get(key)?.takeIf { it.isJsonArray }?.asJsonArray
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
            }
        }
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
        val json = runCatching { NativeJsonDocument.parse(raw) }.getOrNull() ?: return false
        return hasDetour(json)
    }
}
