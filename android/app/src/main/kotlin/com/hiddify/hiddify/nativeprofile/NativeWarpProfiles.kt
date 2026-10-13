package com.hiddify.hiddify.nativeprofile

import com.google.gson.JsonObject
import java.util.Base64

enum class NativeWarpMode { STANDARD, AWG, GENERATED_AWG }

/** Local catalog entries cannot be supplied or redirected by the remote free-profile feed. */
object NativeWarpProfiles {
    val profiles: List<NativeFreeProfile> = listOf(
        profile(NativeWarpMode.STANDARD, "Cloudflare WARP", "Cloudflare WARP",
            listOf("Automatic registration", "WireGuard"), listOf("Автоматическая регистрация", "WireGuard")),
        profile(NativeWarpMode.AWG, "WARP + AmneziaWG", "WARP + AmneziaWG",
            listOf("Automatic registration", "AWG obfuscation"), listOf("Автоматическая регистрация", "Маскировка AWG")),
        profile(NativeWarpMode.GENERATED_AWG, "WARP + AWG — backup generators", "WARP + AWG — резервные генераторы",
            listOf("4 sources", "Automatic fallback"), listOf("4 источника", "Автоматический резерв")),
    )

    private fun profile(mode: NativeWarpMode, en: String, ru: String, tagsEn: List<String>, tagsRu: List<String>): NativeFreeProfile {
        val generated = mode == NativeWarpMode.GENERATED_AWG
        return NativeFreeProfile(emptyList(), en, ru, "builtin:warp-${mode.name.lowercase()}", tagsEn, tagsRu,
            if (generated) "The app requests a new WARP configuration from third-party generators: warp.llimonix.pw, topor-warp.vercel.app, warp-gen.vercel.app and config-generator-warp.vercel.app. These services generate and can know the configuration's private key. Invalid or unavailable sources are skipped. The result is checked by the VPN engine and saved locally; updating a subscription will not replace its keys. To generate another configuration, add this profile again. Availability depends on your network. This uses free Cloudflare WARP, not a paid WARP+ subscription."
            else "The VPN engine registers a free WARP account directly with Cloudflare on first connection and caches it on this device. No third-party configuration generator is used. ${if (mode == NativeWarpMode.AWG) "AmneziaWG adds junk packets while preserving WARP-compatible handshakes. " else ""}Availability depends on your network. This does not include a paid WARP+ subscription.",
            if (generated) "Приложение запрашивает новый конфиг WARP у сторонних генераторов: warp.llimonix.pw, topor-warp.vercel.app, warp-gen.vercel.app и config-generator-warp.vercel.app. Эти сервисы создают конфиг и могут знать его приватный ключ. Недоступные источники и некорректные конфиги пропускаются. Результат проверяется VPN-ядром и сохраняется локально; обновление подписки не заменяет его ключи. Чтобы создать другой конфиг, добавьте этот профиль ещё раз. Доступность зависит от вашей сети. Используется бесплатный Cloudflare WARP, без платной подписки WARP+."
            else "VPN-ядро регистрирует бесплатный аккаунт WARP напрямую в Cloudflare при первом подключении и сохраняет его на этом устройстве. Сторонние генераторы конфигов не используются. ${if (mode == NativeWarpMode.AWG) "AmneziaWG добавляет шумовые пакеты, сохраняя совместимость handshake с WARP. " else ""}Доступность зависит от вашей сети. Платная подписка WARP+ не включена.",
            emptySet(), mode)
    }

    /** WARP endpoints lazily register through the core and retain credentials in its cache. */
    fun content(mode: NativeWarpMode): String {
        require(mode != NativeWarpMode.GENERATED_AWG)
        val endpoint = JsonObject().apply {
            addProperty("type", "warp")
            addProperty("tag", if (mode == NativeWarpMode.AWG) "WARP + AWG" else "WARP")
            addProperty("unique_identifier", if (mode == NativeWarpMode.AWG) "vetroff-free-warp-awg" else "vetroff-free-warp")
            addProperty("mtu", 1280)
            if (mode == NativeWarpMode.AWG) add("awg", JsonObject().apply {
                addProperty("jc", 4); addProperty("jmin", 40); addProperty("jmax", 70)
                // WARP is a standard WireGuard server: do not alter handshake/data headers or sizes.
                addProperty("s1", 0); addProperty("s2", 0); addProperty("s3", 0); addProperty("s4", 0)
                for (i in 1..4) addProperty("h$i", i.toString())
            })
        }
        return JsonObject().apply {
            add("endpoints", com.google.gson.JsonArray().apply { add(endpoint) })
        }.toString()
    }

    /** Decode only documented generator envelopes, never HTML, remote URLs or executable scripts. */
    fun generatedContent(response: String, source: Int): String {
        require(response.toByteArray(Charsets.UTF_8).size <= NativeProfileTransfer.MAX_CONFIG_BYTES)
        fun field(obj: JsonObject, key: String): String {
            val value = obj.get(key)
            require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Invalid WARP response" }
            return value.asString
        }
        val conf = when (source) {
            0 -> {
                val root = NativeJsonDocument.parse(response).asJsonObject
                require(root.get("success")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean } == true) { "WARP generation failed" }
                NativeSubscriptionContent.utf8(Base64.getDecoder().decode(field(root.getAsJsonObject("content"), "configBase64")))
            }
            1 -> response
            2 -> field(NativeJsonDocument.parse(response).asJsonObject, "config")
            3 -> NativeSubscriptionContent.utf8(Base64.getDecoder().decode(field(NativeJsonDocument.parse(response).asJsonObject, "content")))
            else -> error("Unknown WARP generator")
        }.trim()
        require(conf.lineSequence().count { it.trim().equals("[Interface]", true) } == 1 &&
            conf.lineSequence().any { it.trim().equals("[Peer]", true) } &&
            listOf("PrivateKey", "PublicKey", "Address", "Endpoint").all { key ->
                Regex("(?im)^\\s*$key\\s*=\\s*\\S+").containsMatchIn(conf)
            } && Regex("(?im)^\\s*(?:Jc|Jmin|Jmax|I[1-5])\\s*=\\s*\\S+").containsMatchIn(conf)) {
            "Generator did not return an AmneziaWG configuration"
        }
        // The core's panel-share parser accepts a base64url native .conf and preserves all AWG fields.
        return "vpn://" + Base64.getUrlEncoder().withoutPadding().encodeToString(conf.toByteArray(Charsets.UTF_8))
    }
}
