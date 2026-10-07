package com.hiddify.hiddify.nativecore

import com.hiddify.hiddify.Settings
import org.json.JSONObject
import java.security.SecureRandom

data class NativeCoreOptions(
    val balancerStrategy: String,
    val resolveDestination: Boolean,
    val logLevel: String,
    val connectionTestUrl: String,
    val urlTestIntervalSeconds: Int,
    val clashApiPort: Int,
    val useXrayCoreWhenPossible: Boolean,
    val remoteDnsAddress: String,
    val remoteDnsStrategy: String,
    val directDnsAddress: String,
    val directDnsStrategy: String,
    val fakeDns: Boolean,
    val strictRoute: Boolean,
    val tunImplementation: String,
    val mixedPort: Int,
    val directPort: Int,
    val mtu: Int,
    val tlsFragment: Boolean,
    val tlsFragmentSize: String,
    val tlsFragmentSleep: String,
    val tlsMixedSniCase: Boolean,
    val tlsPadding: Boolean,
    val tlsPaddingSize: String,
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
        val tls = root.optJSONObject("tls-tricks") ?: JSONObject()
        return NativeCoreOptions(
            balancerStrategy = root.optString("balancer-strategy", "round-robin"),
            resolveDestination = root.optBoolean("resolve-destination", false),
            logLevel = root.optString("log-level", "warn"),
            connectionTestUrl = root.optString("connection-test-url", "http://cp.cloudflare.com/"),
            urlTestIntervalSeconds = root.optInt("url-test-interval", 600).coerceAtLeast(1),
            clashApiPort = root.optInt("clash-api-port", 16756).validPortOr(16756),
            useXrayCoreWhenPossible = root.optBoolean("use-xray-core-when-possible", false),
            remoteDnsAddress = root.optString("remote-dns-address", "1.1.1.1"),
            remoteDnsStrategy = root.optString("remote-dns-domain-strategy", ""),
            directDnsAddress = root.optString("direct-dns-address", "1.1.1.1"),
            directDnsStrategy = root.optString("direct-dns-domain-strategy", ""),
            fakeDns = root.optBoolean("enable-fake-dns", false),
            strictRoute = root.optBoolean("strict-route", true),
            tunImplementation = root.optString("tun-implementation", "mixed"),
            mixedPort = root.optInt("mixed-port", 12334).takeIf { it in 1..65535 } ?: 0,
            directPort = root.optInt("direct-port", 12337).takeIf { it in 1..65535 } ?: 0,
            mtu = root.optInt("mtu", 9000).coerceIn(1280, 65535),
            tlsFragment = tls.optBoolean("enable-fragment", false),
            tlsFragmentSize = tls.optString("fragment-size", "10-100"),
            tlsFragmentSleep = tls.optString("fragment-sleep", "50-200"),
            tlsMixedSniCase = tls.optBoolean("mixed-sni-case", false),
            tlsPadding = tls.optBoolean("enable-padding", false),
            tlsPaddingSize = tls.optString("padding-size", "1200-1500"),
        )
    }

    fun save(options: NativeCoreOptions): NativeCoreOptions =
        update { root ->
            root.put("balancer-strategy", options.balancerStrategy.takeIf(balancerChoices::contains) ?: "round-robin")
            root.put("resolve-destination", options.resolveDestination)
            root.put("log-level", options.logLevel.takeIf(logLevelChoices::contains) ?: "warn")
            root.put("connection-test-url", options.connectionTestUrl.trim())
            root.put("url-test-interval", options.urlTestIntervalSeconds.coerceIn(60, 86_400))
            root.put("clash-api-port", options.clashApiPort.validPortOr(16756))
            root.put("use-xray-core-when-possible", options.useXrayCoreWhenPossible)

            root.put("remote-dns-address", options.remoteDnsAddress.trim())
            root.put(
                "remote-dns-domain-strategy",
                options.remoteDnsStrategy.takeIf(domainStrategyChoices::contains) ?: "",
            )
            root.put("direct-dns-address", options.directDnsAddress.trim())
            root.put(
                "direct-dns-domain-strategy",
                options.directDnsStrategy.takeIf(domainStrategyChoices::contains) ?: "",
            )
            root.put("enable-fake-dns", options.fakeDns)

            root.put("strict-route", options.strictRoute)
            root.put("tun-implementation", options.tunImplementation.takeIf(tunChoices::contains) ?: "mixed")
            root.put("mixed-port", options.mixedPort.takeIf { it in 1..65535 } ?: 0)
            root.put("direct-port", options.directPort.takeIf { it in 1..65535 } ?: 0)
            root.put("mtu", options.mtu.coerceIn(1280, 65535))

            val tls = root.optJSONObject("tls-tricks") ?: JSONObject()
            tls.put("enable-fragment", options.tlsFragment)
            tls.put("fragment-size", options.tlsFragmentSize.trim())
            tls.put("fragment-sleep", options.tlsFragmentSleep.trim())
            tls.put("mixed-sni-case", options.tlsMixedSniCase)
            tls.put("enable-padding", options.tlsPadding)
            tls.put("padding-size", options.tlsPaddingSize.trim())
            root.put("tls-tricks", tls)
        }

    fun update(transform: (JSONObject) -> Unit): NativeCoreOptions {
        val root = root()
        transform(root)
        Settings.configOptions = root.toString()
        return load()
    }

    fun setBalancerStrategy(value: String) =
        update { it.put("balancer-strategy", value.takeIf(balancerChoices::contains) ?: "round-robin") }

    fun setResolveDestination(value: Boolean) = update { it.put("resolve-destination", value) }

    fun setLogLevel(value: String) =
        update { it.put("log-level", value.takeIf(logLevelChoices::contains) ?: "warn") }

    fun setConnectionTestUrl(value: String) =
        update { it.put("connection-test-url", value.trim()) }

    fun setUrlTestIntervalMinutes(minutes: Int) =
        update { it.put("url-test-interval", minutes.coerceIn(1, 1440) * 60) }

    fun setClashApiPort(port: Int) =
        update { it.put("clash-api-port", port.validPortOr(16756)) }

    fun setUseXrayCoreWhenPossible(value: Boolean) =
        update { it.put("use-xray-core-when-possible", value) }

    fun setRemoteDnsAddress(value: String) =
        update { it.put("remote-dns-address", value.trim()) }

    fun setRemoteDnsStrategy(value: String) =
        update { it.put("remote-dns-domain-strategy", value.takeIf(domainStrategyChoices::contains) ?: "") }

    fun setDirectDnsAddress(value: String) =
        update { it.put("direct-dns-address", value.trim()) }

    fun setDirectDnsStrategy(value: String) =
        update { it.put("direct-dns-domain-strategy", value.takeIf(domainStrategyChoices::contains) ?: "") }

    fun setFakeDns(value: Boolean) = update { it.put("enable-fake-dns", value) }

    fun setStrictRoute(value: Boolean) = update { it.put("strict-route", value) }

    fun setTunImplementation(value: String) =
        update { it.put("tun-implementation", value.takeIf(tunChoices::contains) ?: "mixed") }

    fun setMixedPort(enabled: Boolean, port: Int) =
        update { it.put("mixed-port", if (enabled) port.validPortOr(12334) else 0) }

    fun setDirectPort(enabled: Boolean, port: Int) =
        update { it.put("direct-port", if (enabled) port.validPortOr(12337) else 0) }

    fun setMtu(value: Int) = update { it.put("mtu", value.coerceIn(1280, 65535)) }

    fun setLanSharing(enabled: Boolean): NativeCoreOptions =
        update { root ->
            root.put("allow-connection-from-lan", enabled)
            if (enabled && root.optString("lan-sharing-password").isBlank()) {
                root.put("lan-sharing-password", generateLanPassword())
            }
        }

    fun lanSharingPassword(): String =
        root().optString("lan-sharing-password").trim()

    fun updateTls(transform: (JSONObject) -> Unit): NativeCoreOptions =
        update { root ->
            val tls = root.optJSONObject("tls-tricks") ?: JSONObject()
            transform(tls)
            root.put("tls-tricks", tls)
        }

    fun setTlsFragment(value: Boolean) = updateTls { it.put("enable-fragment", value) }
    fun setTlsFragmentSize(value: String) = updateTls { it.put("fragment-size", value.trim()) }
    fun setTlsFragmentSleep(value: String) = updateTls { it.put("fragment-sleep", value.trim()) }
    fun setTlsMixedSniCase(value: Boolean) = updateTls { it.put("mixed-sni-case", value) }
    fun setTlsPadding(value: Boolean) = updateTls { it.put("enable-padding", value) }
    fun setTlsPaddingSize(value: String) = updateTls { it.put("padding-size", value.trim()) }

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
