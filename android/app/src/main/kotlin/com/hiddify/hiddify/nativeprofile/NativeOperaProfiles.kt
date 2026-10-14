package com.hiddify.hiddify.nativeprofile

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.net.InetAddress
import java.util.Locale

/** One catalog entry; legacy regional source IDs remain updateable. */
object NativeOperaProfiles {
    const val SOURCE = "builtin:opera"
    val regions = linkedMapOf("EU" to "🇪🇺 Europe", "AS" to "🌏 Asia", "AM" to "🌎 Americas")
    fun region(url: String?): String? = if (url == SOURCE) "ALL" else regions.keys.firstOrNull { url == "builtin:opera-$it" }

    fun introduction(russian: Boolean) = if (russian)
        "Бесплатный неофициальный прокси Opera. Регион и сервер выбираются в списке серверов — вручную или по задержке."
        else "A free unofficial Opera proxy. Choose a region and server in the server list, manually or by latency."

    fun noticePoints(russian: Boolean): List<Pair<String, String>> = if (russian) listOf(
        "Только TCP" to "Сайты работают через HTTPS/TCP. UDP, QUIC, многие игры и звонки не поддерживаются.",
        "Доступность" to "Скорость и обход блокировок не гарантируются. Возможны CAPTCHA и ограничения сервисов.",
        "Конфиденциальность" to "Opera видит ваш IP и адреса подключений. Это не гарантия анонимности; правила прямого доступа продолжают действовать.",
        "Обновление" to "Доступ временный: оставьте автообновление включённым. Иногда нужно переподключиться. Не публикуйте конфиг — в нём данные доступа.",
    ) else listOf(
        "TCP only" to "Websites use HTTPS/TCP. UDP, QUIC, many games and calls are not supported.",
        "Availability" to "Speed and unblocking are not guaranteed. CAPTCHA and service restrictions are possible.",
        "Privacy" to "Opera sees your IP and connection destinations. This does not guarantee anonymity; direct routing exclusions still apply.",
        "Updates" to "Access is temporary: keep auto-update enabled. Reconnecting may be necessary. Do not publish the config: it contains access credentials.",
    )

    private fun consent(russian: Boolean) = introduction(russian) + "\n\n" + noticePoints(russian)
        .joinToString("\n\n") { (title, text) -> "• **$title.** $text" }

    val profiles = listOf(NativeFreeProfile(emptyList(), "Opera Proxy", "Opera Proxy", SOURCE,
        listOf("3 regions", "TCP only"), listOf("3 региона", "Только TCP"),
        consent(false), consent(true), emptySet()))

    /** Known region hostnames can be used when SurfEasy discovery is unavailable (e.g. API 801). */
    internal fun fallback(region: String) = JsonArray().apply {
        require(region in regions)
        add(JsonObject().apply { addProperty("ip", "${region.lowercase(Locale.ROOT)}0.sec-tunnel.com") })
    }

    internal fun content(region: String, login: String, password: String, servers: JsonArray): String =
        content(mapOf(region to servers), login, password)

    internal fun content(discoveries: Map<String, JsonArray>, login: String, password: String): String {
        require(login.isNotBlank() && password.isNotBlank())
        val outbounds = JsonArray()
        discoveries.forEach { (region, servers) ->
            require(region in regions)
            val certificateName = "${region.lowercase(Locale.ROOT)}0.sec-tunnel.com"
            val unique = mutableSetOf<String>()
            servers.take(16).forEach { item ->
                val server = item.asJsonObject
                val host = server.get("ip").asString
                require(host == certificateName || isPublicLiteral(host)) { "Invalid Opera server address" }
                val ports = server.getAsJsonArray("ports")
                val port = if (ports == null || ports.size() == 0) 443 else ports[0].asInt
                require(port in 1..65535)
                if (unique.add("$host:$port")) outbounds.add(JsonObject().apply {
                    addProperty("type", "http"); addProperty("tag", "Opera · ${regions.getValue(region)} · ${unique.size}")
                    addProperty("server", host); addProperty("server_port", port)
                    addProperty("username", login); addProperty("password", password)
                    add("tls", JsonObject().apply {
                        addProperty("enabled", true)
                        // Same no-SNI handshake as opera-proxy; server_name still verifies the certificate.
                        addProperty("disable_sni", true); addProperty("server_name", certificateName)
                    })
                })
            }
        }
        require(outbounds.size() > 0) { "Opera returned no servers" }
        return JsonObject().apply { add("outbounds", outbounds) }.toString()
    }

    internal fun isPublicLiteral(host: String): Boolean {
        val ipv4 = host.split('.').let { p -> p.size == 4 && p.all { it.matches(Regex("[0-9]{1,3}")) && it.toInt() in 0..255 } }
        val ipv6 = ':' in host && host.matches(Regex("[0-9a-fA-F:]+"))
        if (!ipv4 && !ipv6) return false
        val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
        return !address.isAnyLocalAddress && !address.isLoopbackAddress && !address.isLinkLocalAddress &&
            !address.isSiteLocalAddress && !address.isMulticastAddress
    }
}
