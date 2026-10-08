package com.hiddify.hiddify.nativeprofile

import java.net.URI
import java.util.Base64

/** ImportSummary from Dart; only aggregate data enters the confirmation UI. */
data class NativeImportSummary(
    val servers: Int,
    val duplicates: Int,
    val unknownTypes: Int,
    val overrides: List<String>,
    val insecureHttp: Boolean = false,
) {
    companion object {
        private val known = setOf(
            "ss", "shadowsocks", "ssr", "vmess", "vless", "trojan", "tuic", "hy", "hysteria",
            "hy2", "hysteria2", "wg", "wireguard", "wireguard_legacy", "awg", "ssh", "socks",
            "http", "shadowtls", "anytls", "snell", "mieru", "warp", "psiphon", "openvpn",
            "openvpn-client", "openvpn-server", "openconnect", "tailscale", "tailcat", "cloudflared",
            "masque", "masque-client", "masque-server", "trusttunnel", "hiddify", "tunnel_client",
            "tunnel_server", "hysteria2_legacy",
        )
        private val utility = setOf("direct", "block", "dns", "selector", "urltest", "url-test", "reject")

        fun parse(raw: String, headers: Set<String> = emptySet(), insecureHttp: Boolean = false): NativeImportSummary {
            var content = raw.trim()
            if (!content.contains("://") && !content.startsWith("{") && !content.contains(':')) {
                content = runCatching {
                    val encoded = content.filterNot(Char::isWhitespace).replace('-', '+').replace('_', '/')
                    String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
                }.getOrDefault(content)
            }
            val overrides = linkedSetOf<String>()
            headers.filterTo(overrides) {
                it.contains("dns") || it.contains("chain") || it.contains("security") ||
                    it.contains("tls") || it.startsWith("enable-")
            }
            val seen = hashSetOf<String>()
            var servers = 0
            var duplicates = 0
            var unknown = 0
            fun add(type: String, fingerprint: String) {
                if (type in utility) return
                servers++
                if (!seen.add(fingerprint)) duplicates++
                if (type !in known) unknown++
            }
            fun summary() = NativeImportSummary(servers, duplicates, unknown, overrides.toList(), insecureHttp)

            val json = runCatching { NativeJsonDocument.parse(content) }.getOrNull()
            if (json?.isJsonObject == true) {
                val root = json.asJsonObject
                listOf("dns", "route", "routing").filterTo(overrides, root::has)
                for (key in listOf("outbounds", "endpoints")) {
                    val entries = root.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: continue
                    for (item in entries) {
                        if (!item.isJsonObject) continue
                        val entry = item.asJsonObject
                        val type = entry.get("type") ?: entry.get("protocol")
                        val name = type?.takeIf { it.isJsonPrimitive }?.asString ?: "unknown"
                        val fingerprint = entry.deepCopy().apply { remove("tag"); remove("name") }
                        add(name, fingerprint.toString())
                    }
                }
                return summary()
            }
            for (line in content.lineSequence()) {
                val text = line.trim()
                val uri = runCatching { URI(text) }.getOrNull() ?: continue
                val scheme = uri.scheme?.lowercase() ?: continue
                if (!text.contains("://") || scheme in setOf("http", "https", "hiddify")) continue
                add(scheme, text.substringBefore('#'))
            }
            if (servers == 0) {
                Regex("^\\s*(?:-\\s*)?type:\\s*([a-zA-Z0-9_-]+)", RegexOption.MULTILINE)
                    .findAll(content).forEach { add(it.groupValues[1], "yaml-${it.range.first}") }
                for (key in listOf("dns", "rules", "rule-providers")) {
                    if (Regex("^$key:", RegexOption.MULTILINE).containsMatchIn(content)) overrides += key
                }
            }
            return summary()
        }
    }
}

