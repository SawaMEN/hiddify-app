package com.hiddify.hiddify.nativeprofile

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Built-in sources are resolved locally, never supplied by the remote free-profile catalog. */
object NativeOperaProfiles {
    private val regions = linkedMapOf("EU" to ("Europe" to "Европа"), "AS" to ("Asia" to "Азия"), "AM" to ("Americas" to "Америка"))
    fun region(url: String?): String? = regions.keys.firstOrNull { url == "builtin:opera-$it" }
    val profiles = regions.map { (code, names) ->
        NativeFreeProfile(emptyList(), "Opera Proxy — ${names.first}", "Opera Proxy — ${names.second}",
            "builtin:opera-$code", listOf("TCP only", "HTTPS proxy"), listOf("Только TCP", "HTTPS-прокси"),
            """**What this is**
An unofficial client for the free proxy infrastructure used by Opera's browser VPN. Opera browser, your own VPS and a paid subscription are not required. This app contacts the Opera/SurfEasy API directly and saves temporary proxy credentials on this device. Selected traffic goes through Opera servers over TLS; the region is approximate, not a specific country.

**Limitations**
- Only TCP is supported. UDP, QUIC/HTTP/3, many games, voice/video calls and WebRTC may fail. Compatible websites can fall back to HTTPS over TCP; this is not guaranteed.
- UDP routed to Opera is rejected by the HTTPS proxy, not sent directly as a fallback. DNS must use the VPN engine's resolver; UDP DNS cannot travel through this proxy.
- Speed, latency, streaming access and availability are not guaranteed. Shared IPs can trigger CAPTCHA or be blocked. The API or proxy servers may be blocked by your provider or changed by Opera.
- Credentials are renewed on connection and by profile updates. Long sessions may require reconnecting. If renewal fails, connection fails; there is no automatic switch to a direct connection. Keep automatic updates enabled.
- This is a third-party service, not a guarantee of anonymity. Its operator sees your source IP and connection destinations; unencrypted HTTP content is visible. HTTPS still protects content between the app and website. Local traffic excluded by your routing rules is outside this proxy.
- Exported configurations contain temporary credentials: do not publish them. Use another profile for applications that require UDP.

[Implementation reference](https://github.com/routerich/opera-proxy) · [Opera privacy policy](https://www.opera.com/privacy)
""".trimIndent(),
            """**Что это значит**
Неофициальное подключение к бесплатной прокси-инфраструктуре встроенного VPN браузера Opera. Браузер Opera, собственный VPS и платная подписка не нужны. Приложение напрямую обращается к API Opera/SurfEasy и сохраняет временные данные доступа на устройстве. Выбранный трафик идёт через серверы Opera по TLS. Выбирается регион, а не конкретная страна.

**Ограничения**
- Поддерживается только TCP. UDP, QUIC/HTTP/3, многие игры, голосовые и видеозвонки, WebRTC могут не работать. Совместимые сайты могут перейти на HTTPS через TCP, но это не гарантируется.
- UDP, направленный в Opera, отклоняется HTTPS-прокси, без запасного выхода напрямую. DNS должен обрабатываться резолвером VPN-ядра: UDP DNS через этот прокси не передаётся.
- Скорость, задержка, доступ к стримингам и работоспособность не гарантируются. Общие IP могут вызывать CAPTCHA и блокировки. Провайдер может заблокировать API или серверы; Opera может изменить сервис.
- Данные доступа обновляются при подключении и обновлении профиля. Долгая сессия может потребовать переподключения. Если обновление не удалось, подключение не запускается; автоматического выхода напрямую нет. Оставьте автообновление включённым.
- Это сторонний сервис, без гарантии анонимности. Оператор видит исходный IP и адреса подключений; содержимое обычного HTTP доступно оператору. HTTPS защищает содержимое между приложением и сайтом. Трафик, исключённый вашими правилами маршрутизации, не проходит через этот прокси.
- Экспортированный конфиг содержит временные данные доступа: не публикуйте его. Для приложений, которым нужен UDP, используйте другой профиль.

[Описание реализации](https://github.com/routerich/opera-proxy) · [Политика конфиденциальности Opera](https://www.opera.com/privacy)
""".trimIndent(), emptySet())
    }

    internal fun content(region: String, login: String, password: String, servers: JsonArray): String {
        require(region in regions && login.isNotBlank() && password.isNotBlank())
        val outbounds = JsonArray()
        servers.take(16).forEachIndexed { index, item ->
            val server = item.asJsonObject
            val ip = server.get("ip").asString
            // API supplies literal IPs. Do not accept hostnames or configuration fragments.
            val ipv4 = ip.split('.').let { parts -> parts.size == 4 && parts.all { part ->
                part.matches(Regex("[0-9]{1,3}")) && part.toInt() in 0..255
            } }
            val ipv6 = ':' in ip && ip.matches(Regex("[0-9a-fA-F:]+")) &&
                runCatching { java.net.InetAddress.getByName(ip) is java.net.Inet6Address }.getOrDefault(false)
            require(ipv4 || ipv6) { "Invalid Opera server address" }
            val ports = server.getAsJsonArray("ports")
            val port = if (ports == null || ports.size() == 0) 443 else ports[0].asInt
            require(port in 1..65535)
            outbounds.add(JsonObject().apply {
                addProperty("type", "http"); addProperty("tag", "Opera $region ${index + 1}")
                addProperty("server", ip); addProperty("server_port", port)
                addProperty("username", login); addProperty("password", password)
                add("tls", JsonObject().apply {
                    addProperty("enabled", true); addProperty("server_name", "${region.lowercase(java.util.Locale.ROOT)}0.sec-tunnel.com")
                })
            })
        }
        require(outbounds.size() > 0) { "Opera returned no servers" }
        return JsonObject().apply {
            add("outbounds", outbounds)
        }.toString()
    }
}
