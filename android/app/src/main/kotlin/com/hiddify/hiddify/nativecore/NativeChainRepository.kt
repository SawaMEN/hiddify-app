package com.hiddify.hiddify.nativecore

import com.hiddify.hiddify.Settings
import org.json.JSONObject

data class NativeChainOptions(
    val status: String = "off",
    val extraMode: String = "warp",
    val unblockerMode: String = "psiphon",
    val extraWarpLicense: String = "",
    val extraPsiphonRegion: String = "AUTO",
    val extraPsiphonConduit: String = "",
    val extraProfileId: String? = null,
    val unblockerWarpLicense: String = "",
    val unblockerWarpCleanIp: String = "auto",
    val unblockerWarpPort: Int = 0,
    val unblockerWarpNoise: String = "1-3",
    val unblockerWarpNoiseMode: String = "m4",
    val unblockerWarpNoiseSize: String = "10-30",
    val unblockerWarpNoiseDelay: String = "10-30",
    val unblockerPsiphonRegion: String = "AUTO",
    val unblockerPsiphonConduit: String = "",
    val unblockerProfileId: String? = null,
)

class NativeChainRepository {
    companion object {
        val statusChoices = listOf("off", "extra_security", "unblocker")
        val modeChoices = listOf("psiphon", "warp", "profile")
        val psiphonRegions =
            listOf(
                "AUTO", "AT", "AU", "BE", "BG", "CA", "CH", "CZ", "DE", "DK", "EE",
                "ES", "FI", "FR", "GB", "HR", "HU", "IE", "IN", "IT", "JP", "LV",
                "NL", "NO", "PL", "PT", "RO", "RS", "SE", "SG", "SK", "US",
            )
    }

    fun load(): NativeChainOptions {
        val root = runCatching { root() }.getOrElse { JSONObject() }
        val extra = root.optJSONObject("extra-security") ?: JSONObject()
        val extraWarp = extra.optJSONObject("warp") ?: JSONObject()
        val extraPsiphon = extra.optJSONObject("psiphon") ?: JSONObject()
        val extraProfile = extra.optJSONObject("profile") ?: JSONObject()

        val unblocker = root.optJSONObject("unblocker") ?: JSONObject()
        val unblockerWarp = unblocker.optJSONObject("warp") ?: JSONObject()
        val unblockerPsiphon = unblocker.optJSONObject("psiphon") ?: JSONObject()
        val unblockerProfile = unblocker.optJSONObject("profile") ?: JSONObject()

        return NativeChainOptions(
            status = root.optString("chain-status", "off").validChoice(statusChoices, "off"),
            extraMode = extra.optString("mode", "warp").validChoice(modeChoices, "warp"),
            unblockerMode = unblocker.optString("mode", "psiphon").validChoice(modeChoices, "psiphon"),
            extraWarpLicense = extraWarp.optString("license-key"),
            extraPsiphonRegion =
                extraPsiphon.optString("region", "AUTO").uppercase()
                    .validChoice(psiphonRegions, "AUTO"),
            extraPsiphonConduit = extraPsiphon.optString("conduit-pairing-id"),
            extraProfileId = extraProfile.nullableString("id"),
            unblockerWarpLicense = unblockerWarp.optString("license-key"),
            unblockerWarpCleanIp = unblockerWarp.optString("clean-ip", "auto"),
            unblockerWarpPort = unblockerWarp.optInt("clean-port", 0).coerceIn(0, 65535),
            unblockerWarpNoise = unblockerWarp.optString("noise", "1-3"),
            unblockerWarpNoiseMode = unblockerWarp.optString("noise-mode", "m4"),
            unblockerWarpNoiseSize = unblockerWarp.optString("noise-size", "10-30"),
            unblockerWarpNoiseDelay = unblockerWarp.optString("noise-delay", "10-30"),
            unblockerPsiphonRegion =
                unblockerPsiphon.optString("region", "AUTO").uppercase()
                    .validChoice(psiphonRegions, "AUTO"),
            unblockerPsiphonConduit = unblockerPsiphon.optString("conduit-pairing-id"),
            unblockerProfileId = unblockerProfile.nullableString("id"),
        )
    }

    fun replaceDeletedProfile(id: String, nextActiveId: String?) {
        val root = runCatching { root() }.getOrElse {
            android.util.Log.w("NativeChainRepository", "Cannot repair references in invalid chain settings", it)
            return
        }
        var changed = false
        for (key in listOf("extra-security", "unblocker")) {
            val profile = root.optJSONObject(key)?.optJSONObject("profile") ?: continue
            if (profile.optString("id") == id) {
                profile.putNullable("id", nextActiveId)
                changed = true
            }
        }
        if (changed) Settings.configOptions = root.toString()
    }

    fun save(value: NativeChainOptions): NativeChainOptions {
        require(value.status in statusChoices && value.extraMode in modeChoices && value.unblockerMode in modeChoices) { "Invalid chain selection" }
        require(value.unblockerWarpPort in 0..65535) { "Invalid WARP port" }
        listOf(value.unblockerWarpNoise, value.unblockerWarpNoiseSize, value.unblockerWarpNoiseDelay)
            .forEach { NativeTlsOptions.normalizeRange(it, allowEmpty = true) }
        val root = root()
        root.put("chain-status", value.status.validChoice(statusChoices, "off"))

        val extra = root.optJSONObject("extra-security") ?: JSONObject()
        extra.put("mode", value.extraMode.validChoice(modeChoices, "warp"))
        extra.objectFor("warp").put("license-key", value.extraWarpLicense.trim())
        extra.objectFor("psiphon").apply {
            put("region", value.extraPsiphonRegion.uppercase().validChoice(psiphonRegions, "AUTO"))
            put("conduit-pairing-id", value.extraPsiphonConduit.trim())
        }
        extra.objectFor("profile").putNullable("id", value.extraProfileId)
        root.put("extra-security", extra)

        val unblocker = root.optJSONObject("unblocker") ?: JSONObject()
        unblocker.put("mode", value.unblockerMode.validChoice(modeChoices, "psiphon"))
        unblocker.objectFor("warp").apply {
            put("license-key", value.unblockerWarpLicense.trim())
            put("clean-ip", value.unblockerWarpCleanIp.trim().ifBlank { "auto" })
            put("clean-port", value.unblockerWarpPort.coerceIn(0, 65535))
            put("noise", value.unblockerWarpNoise.trim())
            put("noise-mode", value.unblockerWarpNoiseMode.trim().ifBlank { "m4" })
            put("noise-size", value.unblockerWarpNoiseSize.trim())
            put("noise-delay", value.unblockerWarpNoiseDelay.trim())
        }
        unblocker.objectFor("psiphon").apply {
            put("region", value.unblockerPsiphonRegion.uppercase().validChoice(psiphonRegions, "AUTO"))
            put("conduit-pairing-id", value.unblockerPsiphonConduit.trim())
        }
        unblocker.objectFor("profile").putNullable("id", value.unblockerProfileId)
        root.put("unblocker", unblocker)

        Settings.configOptions = root.toString()
        return load()
    }

    /** Quick menu changes only the selected stage and status in the latest persisted options. */
    fun saveSelection(extra: Boolean, mode: String?): NativeChainOptions {
        require(mode == null || mode in modeChoices) { "Invalid chain mode" }
        val root = root()
        val stage = if (extra) "extra-security" else "unblocker"
        val status = if (extra) "extra_security" else "unblocker"
        if (mode == null) {
            if (root.optString("chain-status") != status) return load()
            root.put("chain-status", "off")
        } else {
            root.objectFor(stage).put("mode", mode)
            root.put("chain-status", status)
        }
        Settings.configOptions = root.toString()
        return load()
    }

    private fun root(): JSONObject =
        Settings.configOptions.trim().takeIf { it.isNotEmpty() }?.let(::JSONObject) ?: JSONObject()

    private fun JSONObject.objectFor(key: String): JSONObject {
        val child = optJSONObject(key) ?: JSONObject()
        put(key, child)
        return child
    }

    private fun JSONObject.putNullable(
        key: String,
        value: String?,
    ) {
        if (value.isNullOrBlank()) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun String.validChoice(
        choices: List<String>,
        fallback: String,
    ): String = takeIf(choices::contains) ?: fallback
}
