package com.hiddify.hiddify.nativecore

import android.content.Context
import org.json.JSONObject

data class NativeDnsOptions(
    val remoteAddress: String = "tcp://8.8.8.8",
    val remoteStrategy: String = "",
    val fakeDns: Boolean = false,
    val directAddress: String = "udp://1.1.1.1",
    val directStrategy: String = "",
) {
    fun validated(): NativeDnsOptions {
        fun address(value: String): String = value.trim().also {
            require(it.isNotEmpty() && it.length <= 2048 && it.none(Char::isISOControl)) { "Invalid DNS address" }
        }
        require(remoteStrategy in NativeConfigChoices.domainStrategyChoices &&
            directStrategy in NativeConfigChoices.domainStrategyChoices) { "Invalid DNS strategy" }
        // Match Dart's nonempty-address validator; do not restrict core-supported resolver schemes.
        return copy(remoteAddress = address(remoteAddress), directAddress = address(directAddress))
    }
}

class NativeDnsOptionsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

    fun load(): NativeDnsOptions {
        val values = preferences.all
        val root = root(values)
        fun text(key: String, fallback: String): String = (root.opt(key) as? String)
            ?: (values["flutter.$key"] as? String) ?: fallback
        fun strategy(key: String) = text(key, "").takeIf { it in NativeConfigChoices.domainStrategyChoices } ?: ""
        return NativeDnsOptions(
            remoteAddress = text("remote-dns-address", "tcp://8.8.8.8"),
            remoteStrategy = strategy("remote-dns-domain-strategy"),
            fakeDns = (root.opt("enable-fake-dns") as? Boolean) ?: (values["flutter.enable-fake-dns"] as? Boolean) ?: false,
            directAddress = text("direct-dns-address", "udp://1.1.1.1"),
            directStrategy = strategy("direct-dns-domain-strategy"),
        )
    }

    /** IO, under the native service lifecycle barrier. Preserve every unrelated option. */
    fun save(input: NativeDnsOptions): NativeDnsOptions {
        val value = input.validated()
        val root = root(preferences.all)
        val fields = mapOf(
            "remote-dns-address" to value.remoteAddress, "remote-dns-domain-strategy" to value.remoteStrategy,
            "direct-dns-address" to value.directAddress, "direct-dns-domain-strategy" to value.directStrategy,
        )
        val editor = preferences.edit()
        fields.forEach { (key, item) -> root.put(key, item); editor.putString("flutter.$key", item) }
        root.put("enable-fake-dns", value.fakeDns)
        editor.putBoolean("flutter.enable-fake-dns", value.fakeDns)
        editor.putString("config_options_json", root.toString())
        check(editor.commit()) { "Could not save DNS settings" }
        return load()
    }

    private fun root(values: Map<String, *>): JSONObject {
        val text = values["config_options_json"] as? String ?: ""
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }
}
