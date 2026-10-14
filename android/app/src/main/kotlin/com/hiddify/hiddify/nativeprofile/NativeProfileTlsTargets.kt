package com.hiddify.hiddify.nativeprofile

import java.net.URI
import java.net.URLDecoder
import java.util.Base64

/** Read existing TLS settings; never guess a certificate name or inspect account credentials. */
internal data class NativeProfileTlsTarget(val tag: String, val serverName: String, val fingerprint: Boolean, val fragment: Boolean)

internal object NativeProfileTlsTargets {
    fun inspect(content: String): List<NativeProfileTlsTarget> = runCatching {
        val decoded = NativeSubscriptionContent.decode(content)
        val result = mutableListOf<NativeProfileTlsTarget>()
        val tcp = setOf("vless", "vmess", "trojan", "http", "socks", "anytls", "shadowtls")
        if (decoded.trimStart().startsWith('{') || decoded.trimStart().startsWith('[')) {
            val root = NativeJsonDocument.parse(decoded)
            fun walk(value: com.google.gson.JsonElement) {
                if (value.isJsonArray) { value.asJsonArray.forEach(::walk); return }
                if (!value.isJsonObject) return
                val node = value.asJsonObject
                node.get("outbounds")?.let { walk(it); return }
                val tls = node.get("tls")?.takeIf { it.isJsonObject }?.asJsonObject
                val type = node.get("type")?.asString
                if (tls?.get("enabled")?.asBoolean == true && type in tcp + setOf("hysteria", "hysteria2", "tuic")) {
                    val reality = tls.get("reality")?.takeIf { it.isJsonObject }?.asJsonObject?.get("enabled")?.asBoolean == true
                    result += NativeProfileTlsTarget(node.get("tag")?.asString.orEmpty(), tls.get("server_name")?.asString.orEmpty(), type in tcp, type in tcp && !reality)
                }
            }
            walk(root)
        } else for (line in decoded.lines().map(String::trim)) runCatching {
            if (line.startsWith("vmess://", true)) {
                val node = NativeJsonDocument.parse(NativeSubscriptionContent.utf8(Base64.getDecoder().decode(line.substringAfter("://").replace('-', '+').replace('_', '/')))).asJsonObject
                if (node.get("tls")?.asString == "tls") result += NativeProfileTlsTarget(node.get("ps")?.asString.orEmpty(), node.get("sni")?.asString.orEmpty(), true, true)
            } else if (line.startsWith("vless://", true) || line.startsWith("trojan://", true)) {
                val uri = URI(line)
                val query = uri.rawQuery.orEmpty().split('&').associate {
                    URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
                }
                val security = query["security"] ?: query["tls"]
                if (security in setOf("tls", "reality")) result += NativeProfileTlsTarget(uri.rawFragment?.let { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.orEmpty(), query["sni"].orEmpty(), true, security != "reality")
            }
        }
        result.distinctBy { it.tag }
    }.getOrDefault(emptyList())
}
