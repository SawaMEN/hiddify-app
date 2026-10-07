package com.hiddify.hiddify.nativecore

import com.hiddify.hiddify.Settings
import org.json.JSONObject
import java.security.SecureRandom

data class NativeCoreOptions(
    val strictRoute: Boolean,
    val tunImplementation: String,
    val mixedPort: Int,
    val directPort: Int,
)

class NativeCoreOptionsRepository {
    companion object {
        val balancerChoices = listOf("round-robin", "consistent-hashing", "sticky-sessions")
        val domainStrategyChoices = listOf("", "prefer_ipv4", "prefer_ipv6", "ipv4_only", "ipv6_only")
        val tunChoices = listOf("mixed", "system", "gvisor")
        val logLevelChoices = listOf("warn", "info", "debug", "trace", "error", "fatal")
    }

    fun load(): NativeCoreOptions {
        val root = root()
        return NativeCoreOptions(
            strictRoute = root.optBoolean("strict-route", true),
            tunImplementation = root.optString("tun-implementation", "gvisor"),
            mixedPort = if (root.optBoolean("enable-mixed-port", true)) root.optInt("mixed-port", 12334).takeIf { it in 1..65535 } ?: 0 else 0,
            directPort = if (root.optBoolean("enable-direct-port", true)) root.optInt("direct-port", 12337).takeIf { it in 1..65535 } ?: 0 else 0,
        )
    }

    fun save(options: NativeCoreOptions): NativeCoreOptions =
        update { root ->
            root.put("strict-route", options.strictRoute)
            root.put("tun-implementation", options.tunImplementation.takeIf(tunChoices::contains) ?: "mixed")
            root.put("enable-mixed-port", options.mixedPort > 0)
            root.put("mixed-port", options.mixedPort.takeIf { it in 1..65535 } ?: 0)
            root.put("enable-direct-port", options.directPort > 0)
            root.put("direct-port", options.directPort.takeIf { it in 1..65535 } ?: 0)
        }

    fun update(transform: (JSONObject) -> Unit): NativeCoreOptions {
        val root = root()
        transform(root)
        Settings.configOptions = root.toString()
        return load()
    }

    fun setStrictRoute(value: Boolean) = update { it.put("strict-route", value) }

    fun setTunImplementation(value: String) =
        update { it.put("tun-implementation", value.takeIf(tunChoices::contains) ?: "mixed") }

    fun setMixedPort(enabled: Boolean, port: Int) =
        update { it.put("enable-mixed-port", enabled); it.put("mixed-port", if (enabled) port.validPortOr(12334) else 0) }

    fun setDirectPort(enabled: Boolean, port: Int) =
        update { it.put("enable-direct-port", enabled); it.put("direct-port", if (enabled) port.validPortOr(12337) else 0) }

    fun setLanSharing(enabled: Boolean): NativeCoreOptions =
        update { root ->
            root.put("allow-connection-from-lan", enabled)
            if (enabled && root.optString("lan-sharing-password").isBlank()) {
                root.put("lan-sharing-password", generateLanPassword())
            }
        }

    fun lanSharingPassword(): String =
        root().optString("lan-sharing-password").trim()

    private fun root(): JSONObject =
        runCatching {
            val raw = Settings.configOptions.trim()
            if (raw.isEmpty()) JSONObject() else JSONObject(raw)
        }.getOrElse { JSONObject() }

    private fun generateLanPassword(): String {
        val bytes = ByteArray(12).also(SecureRandom()::nextBytes)
        return bytes.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun Int.validPortOr(fallback: Int): Int =
        if (this in 1..65535) this else fallback
}
