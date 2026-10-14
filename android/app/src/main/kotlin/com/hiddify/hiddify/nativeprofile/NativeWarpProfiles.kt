package com.hiddify.hiddify.nativeprofile

import com.google.gson.JsonObject

enum class NativeWarpMode { STANDARD, AWG, GENERATED_AWG }

/** Local catalog entries cannot be supplied or redirected by the remote free-profile feed. */
object NativeWarpProfiles {
    val profiles: List<NativeFreeProfile> = listOf(
        profile(NativeWarpMode.STANDARD, "Cloudflare WARP", "Cloudflare WARP",
            listOf("Automatic registration", "WireGuard"), listOf("Автоматическая регистрация", "WireGuard")),
        profile(NativeWarpMode.AWG, "WARP + AmneziaWG", "WARP + AmneziaWG",
            listOf("Automatic registration", "AWG obfuscation"), listOf("Автоматическая регистрация", "Маскировка AWG")),
        profile(NativeWarpMode.GENERATED_AWG, "WARP + AWG — backup generators", "WARP + AWG — резервные генераторы",
            listOf("3 sources", "Automatic fallback"), listOf("3 источника", "Автоматический резерв")),
    )

    private fun profile(mode: NativeWarpMode, en: String, ru: String, tagsEn: List<String>, tagsRu: List<String>): NativeFreeProfile {
        val generated = mode == NativeWarpMode.GENERATED_AWG
        val operatorEn = if (generated) "The app requests a new WARP configuration from third-party generators: warp-config-generator-theta.vercel.app, dulcet-fox-556b08.netlify.app and santa-atmo.ru. These services generate and can know the private key. Invalid or unavailable sources are skipped."
            else "The app registers a free WARP account directly with Cloudflare before saving the profile. The private key is generated and retained on this device; only the public key is sent to Cloudflare. No third-party generator is used."
        val operatorRu = if (generated) "Приложение запрашивает новый конфиг WARP у сторонних генераторов: warp-config-generator-theta.vercel.app, dulcet-fox-556b08.netlify.app и santa-atmo.ru. Они создают конфиг и могут знать приватный ключ. Недоступные источники и некорректные ответы пропускаются."
            else "Приложение регистрирует бесплатный аккаунт WARP напрямую в Cloudflare до сохранения профиля. Приватный ключ создаётся и остаётся на устройстве; Cloudflare получает только публичный ключ. Сторонние генераторы не используются."
        return NativeFreeProfile(emptyList(), en, ru, "builtin:warp-${mode.name.lowercase(java.util.Locale.ROOT)}", tagsEn, tagsRu,
            "$operatorEn The configuration is validated by the VPN engine and saved locally. Subscription refresh does not replace the keys. Add the profile again to generate another account. ${if (mode != NativeWarpMode.STANDARD) "AmneziaWG sends junk/signature packets while preserving WARP-compatible handshakes. " else ""}Registration and UDP access can be blocked by your provider; saving a configuration does not confirm tunnel connectivity. This uses free WARP and does not include a paid WARP+ license.",
            "$operatorRu Конфиг проверяется VPN-ядром и сохраняется локально. Обновление подписки не меняет ключи. Чтобы создать другой аккаунт, добавьте профиль ещё раз. ${if (mode != NativeWarpMode.STANDARD) "AmneziaWG отправляет шумовые и signature-пакеты, сохраняя совместимость handshake с WARP. " else ""}Провайдер может блокировать регистрацию или UDP: сохранение конфига ещё не подтверждает передачу трафика. Используется бесплатный WARP, без платной лицензии WARP+.",
            emptySet(), mode)
    }

    /** Only migrate exact profiles emitted by the first implementation; preserve custom edits. */
    fun legacyMode(raw: String): NativeWarpMode? {
        val document = runCatching { NativeJsonDocument.parse(raw) }.getOrNull() ?: return null
        return listOf(NativeWarpMode.STANDARD, NativeWarpMode.AWG).firstOrNull {
            document == NativeJsonDocument.parse(legacyContent(it))
        }
    }

    /** Historical lazy template, retained solely to recognize and repair existing saved profiles. */
    internal fun legacyContent(mode: NativeWarpMode): String {
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

}
