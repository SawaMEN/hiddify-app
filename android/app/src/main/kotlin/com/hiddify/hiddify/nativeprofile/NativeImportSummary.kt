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
    val unsupportedProtocols: List<String> = emptyList(),
) {
    companion object {
        private val known = setOf(
            "ss", "shadowsocks", "ssr", "vmess", "vless", "trojan", "tuic", "hy", "hysteria",
            "hy2", "hysteria2", "wg", "wireguard", "wireguard_legacy", "awg", "ssh", "socks",
            "http", "shadowtls", "anytls", "snell", "mieru", "warp", "psiphon", "openvpn",
            "openvpn-client", "openvpn-server", "openconnect", "tailscale", "tailcat", "cloudflared",
            "masque", "masque-client", "masque-server", "trusttunnel", "hiddify", "tunnel_client",
            "tunnel_server", "hysteria2_legacy", "vpn", "tt", "mierus", "naive", "naive+https", "naive+quic", "socks5", "sudoku", "fptn", "openflux", "pingtunnel",
        )
        private val unavailable = mapOf(
            "vk-turn-proxy" to "VK TURN Proxy", "wingsv" to "VK TURN Proxy",
            "tg" to "MTProto (Telegram)", "mtproto" to "MTProto (Telegram)",
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
            val unsupported = linkedSetOf<String>()
            fun add(type: String, fingerprint: String) {
                if (type in utility) return
                servers++
                if (!seen.add(fingerprint)) duplicates++
                if (type !in known) unknown++
                unavailable[type]?.let(unsupported::add)
            }
            fun summary() = NativeImportSummary(servers, duplicates, unknown, overrides.toList(), insecureHttp, unsupported.toList())

            val json = runCatching { NativeJsonDocument.parse(content) }.getOrNull()
            if (json != null) {
                fun visit(value: com.google.gson.JsonElement) {
                    if (value.isJsonArray) { value.asJsonArray.forEach(::visit); return }
                    if (!value.isJsonObject) return
                    val root = value.asJsonObject
                    listOf("dns", "route", "routing").filterTo(overrides, root::has)
                    if (root.has("outbounds") || root.has("endpoints")) {
                        for (key in listOf("outbounds", "endpoints")) {
                            root.get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.forEach(::visit)
                        }
                    } else {
                        val type = root.get("type") ?: root.get("protocol") ?: return
                        val name = type.takeIf { it.isJsonPrimitive }?.asString?.lowercase() ?: "unknown"
                        val fingerprint = root.deepCopy().apply { remove("tag"); remove("name") }
                        add(name, fingerprint.toString())
                    }
                }
                visit(json)
                return summary()
            }
            for (line in content.lineSequence()) {
                val text = line.trim()
                val uri = runCatching { URI(text) }.getOrNull() ?: continue
                val scheme = uri.scheme?.lowercase() ?: continue
                if ((!text.contains("://") && scheme != "fptn") || scheme in setOf("http", "https", "hiddify")) continue
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

