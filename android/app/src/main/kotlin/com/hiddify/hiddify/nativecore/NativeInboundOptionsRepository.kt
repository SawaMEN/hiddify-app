package com.hiddify.hiddify.nativecore

import android.content.Context
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64

data class NativeInboundOptions(
    val strictRoute: Boolean = true,
    val tunImplementation: String = "gvisor",
    val mixedEnabled: Boolean = true,
    val mixedPort: Int = 12334,
    val directEnabled: Boolean = true,
    val directPort: Int = 12337,
    val allowLan: Boolean = false,
    val lanPassword: String = "",
) {
    fun validated(): NativeInboundOptions {
        require(tunImplementation in listOf("mixed", "system", "gvisor")) { "Invalid TUN implementation" }
        require(mixedPort in 1..65535 && directPort in 1..65535) { "Ports must be between 1 and 65535" }
        require(!mixedEnabled || !directEnabled || mixedPort != directPort) { "Enabled listeners must use different ports" }
        require(lanPassword.length <= 128 && lanPassword.none { it.isISOControl() }) { "Invalid LAN password" }
        return this
    }
}

class NativeInboundOptionsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

    fun load(): NativeInboundOptions {
        val values = preferences.all
        val root = root(values)
        fun flag(key: String, fallback: Boolean) = if (root.has(key)) root.optBoolean(key, fallback)
            else values["flutter.$key"] as? Boolean ?: fallback
        fun port(key: String, fallback: Int): Int = root.optInt(key, -1).takeIf { it in 1..65535 }
            ?: (values["flutter.$key"] as? Number)?.toInt()?.takeIf { it in 1..65535 } ?: fallback
        return NativeInboundOptions(
            strictRoute = flag("strict-route", true),
            tunImplementation = root.optString("tun-implementation",
                values["flutter.tun-implementation"] as? String ?: "gvisor").takeIf { it in listOf("mixed", "system", "gvisor") } ?: "gvisor",
            mixedEnabled = flag("enable-mixed-port", true) && root.optInt("mixed-port", 12334) != 0,
            mixedPort = port("mixed-port", 12334),
            directEnabled = flag("enable-direct-port", true) && root.optInt("direct-port", 12337) != 0,
            directPort = port("direct-port", 12337),
            allowLan = flag("allow-connection-from-lan", false),
            lanPassword = root.optString("lan-sharing-password", values["flutter.lan_sharing_password"] as? String ?: ""),
        )
    }

    /** Called on IO while the native core is stopped. Merge only this page's keys. */
    fun save(input: NativeInboundOptions): NativeInboundOptions {
        val value = input.validated()
        val root = root(preferences.all)
        val password = if (value.allowLan && value.lanPassword.isBlank()) {
            Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(18).also { SecureRandom().nextBytes(it) })
        } else value.lanPassword
        val fields = mapOf<String, Any>(
            "strict-route" to value.strictRoute, "tun-implementation" to value.tunImplementation,
            "enable-mixed-port" to value.mixedEnabled, "mixed-port" to value.mixedPort,
            "enable-direct-port" to value.directEnabled, "direct-port" to value.directPort,
            "allow-connection-from-lan" to value.allowLan,
        )
        val editor = preferences.edit()
        fields.forEach { (key, item) ->
            root.put(key, item)
            when (item) {
                is Boolean -> editor.putBoolean("flutter.$key", item)
                is Int -> editor.putLong("flutter.$key", item.toLong())
                is String -> editor.putString("flutter.$key", item)
            }
        }
        // Older native editors used port zero instead of the enable flag; keep both aligned.
        root.put("mixed-port", if (value.mixedEnabled) value.mixedPort else 0)
        root.put("direct-port", if (value.directEnabled) value.directPort else 0)
        root.put("lan-sharing-password", password)
        editor.putString("flutter.lan_sharing_password", password)
        editor.putString("config_options_json", root.toString())
        check(editor.commit()) { "Could not save inbound settings" }
        return load()
    }

    private fun root(values: Map<String, *>): JSONObject {
        val text = values["config_options_json"] as? String ?: ""
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }
}
