package com.hiddify.hiddify.nativeprofile

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.net.InetAddress
import java.net.Inet4Address
import java.net.Inet6Address
import java.util.Base64

/** Registered account material. Never use a data class/toString that could expose credentials. */
internal class NativeWarpAccount private constructor(
    private val privateKey: String,
    private val publicKey: String,
    private val addresses: List<String>,
    private val host: String,
    private val port: Int,
    private val reserved: ByteArray?,
    private val awg: JsonObject,
) {
    fun content(mode: NativeWarpMode): String {
        val useAwg = mode != NativeWarpMode.STANDARD
        val peer = JsonObject().apply {
            addProperty("address", host); addProperty("port", port); addProperty("public_key", publicKey)
            addProperty("persistent_keepalive_interval", 25)
            add("allowed_ips", JsonArray().apply { add("0.0.0.0/0"); if (addresses.any { ':' in it }) add("::/0") })
            // Current AWG endpoint schema has no reserved option. WireGuard supports client_id.
            if (!useAwg && reserved != null) add("reserved", JsonArray().apply { reserved.forEach { add(it.toInt() and 255) } })
        }
        val endpoint = JsonObject().apply {
            addProperty("type", if (useAwg) "awg" else "wireguard")
            addProperty("tag", if (useAwg) "WARP + AWG" else "WARP")
            addProperty("private_key", privateKey); addProperty("mtu", 1280)
            add("address", JsonArray().apply { addresses.forEach(::add) })
            add("peers", JsonArray().apply { add(peer) })
            if (useAwg) {
                addProperty("useIntegratedTun", false)
                add("awg", awg.deepCopy().apply {
                    // The WARP server is standard WireGuard. Obfuscate before the real handshake.
                    for (i in 1..4) { addProperty("s$i", 0); addProperty("h$i", i.toString()) }
                    if (!has("jc")) addProperty("jc", 4)
                    if (!has("jmin")) addProperty("jmin", 40)
                    if (!has("jmax")) addProperty("jmax", 70)
                    if ((1..5).none { get("i$it")?.asString?.isNotBlank() == true }) addProperty("i1", NativeWarpSignature.QUIC)
                })
            } else addProperty("system", false)
        }
        return JsonObject().apply { add("endpoints", JsonArray().apply { add(endpoint) }) }.toString()
    }

    companion object {
        /** Accept actual generator/Cloudflare envelopes, independent of source order. */
        fun parse(response: String, localPrivateKey: String? = null): NativeWarpAccount {
            val text = response.removePrefix("\uFEFF").trim()
            require(text.toByteArray().size <= NativeProfileTransfer.MAX_CONFIG_BYTES) { "WARP response too large" }
            if (text.lineSequence().any { it.trim().equals("[Interface]", true) }) return parseConf(text)
            val root = NativeJsonDocument.parse(text).asJsonObject
            root.get("success")?.let { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean) { "WARP registration failed" } }
            val profile = root.get("result")?.takeIf { it.isJsonObject }?.asJsonObject ?: root
            if (profile.get("config")?.isJsonObject == true) return parseCloudflare(profile, localPrivateKey)
            val content = root.get("content")
            val config = when {
                content?.isJsonObject == true -> field(content.asJsonObject, "configBase64")
                content?.isJsonPrimitive == true && content.asJsonPrimitive.isString -> content.asString
                root.get("config")?.isJsonPrimitive == true && root.getAsJsonPrimitive("config").isString -> field(root, "config")
                else -> error("No WARP configuration in response")
            }
            val conf = if (config.trimStart().startsWith("[Interface]")) config
                else NativeSubscriptionContent.utf8(Base64.getDecoder().decode(config))
            return parseConf(conf)
        }

        private fun parseCloudflare(profile: JsonObject, localPrivateKey: String?): NativeWarpAccount {
            profile.get("warp_enabled")?.let { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean) { "WARP account disabled" } }
            val config = profile.getAsJsonObject("config")
            val address = config.getAsJsonObject("interface").getAsJsonObject("addresses")
            val ips = listOfNotNull(optionalField(address, "v4"), optionalField(address, "v6")).map(::prefix)
            val peers = config.getAsJsonArray("peers")
            require(peers.size() > 0) { "Missing WARP peer" }
            val peer = peers[0].asJsonObject
            val endpoint = peer.getAsJsonObject("endpoint")
            // Literal IPv4 avoids poisoned DNS and an IPv6-only peer on an IPv4 mobile network.
            val v4 = field(endpoint, "v4").substringBeforeLast(':')
            require(ip(v4) is Inet4Address) { "Missing WARP IPv4 endpoint" }
            val hostPort = optionalField(endpoint, "host")?.substringAfterLast(':')?.toIntOrNull()
            val ports = endpoint.getAsJsonArray("ports")?.mapNotNull { runCatching { it.asInt }.getOrNull() }?.filter { it in 1..65535 }.orEmpty()
            val port = hostPort?.takeIf { it in ports } ?: ports.firstOrNull() ?: error("Missing WARP port")
            val clientId = optionalField(config, "client_id")?.let { Base64.getDecoder().decode(it).also { bytes -> require(bytes.size == 3) } }
            // Direct registration never trusts a remotely supplied private key.
            val privateKey = localPrivateKey ?: optionalField(config, "private_key") ?: field(profile, "key")
            return create(privateKey, field(peer, "public_key"), ips, v4, port, clientId, JsonObject())
        }

        private fun parseConf(raw: String): NativeWarpAccount {
            var section = ""
            val iface = mutableMapOf<String, String>()
            val peer = mutableMapOf<String, String>()
            var peers = 0
            for (original in raw.lineSequence()) {
                val line = original.trim()
                if (line.isEmpty() || line.startsWith('#') || line.startsWith(';')) continue
                if (line.startsWith('[') && line.endsWith(']')) {
                    section = line.lowercase(java.util.Locale.ROOT)
                    require(section in setOf("[interface]", "[peer]")) { "Invalid WARP configuration section" }
                    if (section == "[peer]") peers++
                    require(peers <= 1) { "Multiple WARP peers are not supported" }
                    continue
                }
                require('=' in line && section.isNotEmpty()) { "Invalid WARP configuration" }
                val key = line.substringBefore('=').trim()
                val value = line.substringAfter('=').trim()
                val target = if (section == "[interface]") iface else peer
                require(target.put(key, value) == null) { "Duplicate WARP configuration field" }
            }
            require(peers == 1) { "Missing WARP peer" }
            val endpoint = peer["Endpoint"] ?: error("Missing WARP endpoint")
            val host = endpoint.substringBeforeLast(':').removeSurrounding("[", "]")
            val port = endpoint.substringAfterLast(':').toIntOrNull() ?: error("Invalid WARP port")
            val options = JsonObject()
            for (key in listOf("Jc", "Jmin", "Jmax")) iface[key]?.let {
                val n = it.toIntOrNull(); require(n != null && n in 0..10000) { "Invalid WARP junk option" }
                options.addProperty(key.lowercase(java.util.Locale.ROOT), n)
            }
            for (i in 1..5) iface["I$i"]?.takeIf { it.isNotBlank() }?.let { options.addProperty("i$i", it) }
            // Reject server-dependent obfuscation that Cloudflare cannot decode.
            for (i in 1..4) {
                require(iface["S$i"]?.let { it == "0" } != false) { "Incompatible WARP packet size" }
                require(iface["H$i"]?.let { it == i.toString() } != false) { "Incompatible WARP header" }
            }
            require(iface["HeaderProtectionKey"].isNullOrBlank()) { "WARP does not support AWG header protection" }
            val reserved = (peer["Reserved"] ?: iface["Reserved"])?.split(',')?.map {
                val n = it.trim().toIntOrNull(); require(n != null && n in 0..255); n.toByte()
            }?.toByteArray()?.also { require(it.size == 3) }
            return create(iface["PrivateKey"] ?: error("Missing WARP private key"), peer["PublicKey"] ?: error("Missing WARP public key"),
                (iface["Address"] ?: error("Missing WARP address")).split(',').map { prefix(it.trim()) }, host, port, reserved, options)
        }

        private fun create(privateKey: String, publicKey: String, addresses: List<String>, host: String, port: Int, reserved: ByteArray?, awg: JsonObject): NativeWarpAccount {
            for (key in listOf(privateKey, publicKey)) require(Base64.getDecoder().decode(key).let { it.size == 32 && it.any { byte -> byte != 0.toByte() } }) { "Invalid WARP key" }
            require(addresses.any { ':' !in it } && port in 1..65535) { "Invalid WARP address or port" }
            require(!ip(host).isAnyLocalAddress && !ip(host).isLoopbackAddress && !ip(host).isSiteLocalAddress) { "Invalid WARP endpoint" }
            return NativeWarpAccount(privateKey, publicKey, addresses, host, port, reserved, awg)
        }

        private fun ip(text: String): InetAddress {
            // Never resolve a hostname supplied by a generator during validation.
            val literal = if (':' in text) text.matches(Regex("[0-9a-fA-F:]+")) else text.split('.').let { parts ->
                parts.size == 4 && parts.all { it.matches(Regex("[0-9]{1,3}")) && it.toInt() in 0..255 }
            }
            require(literal) { "WARP endpoint must be a literal IP" }
            return InetAddress.getByName(text)
        }
        private fun prefix(text: String): String {
            val address = ip(text.substringBefore('/'))
            val bits = if (address is Inet6Address) 128 else 32
            val length = if ('/' in text) text.substringAfter('/').toIntOrNull() ?: error("Invalid WARP prefix") else bits
            require(length in 1..bits && !address.isAnyLocalAddress) { "Invalid WARP prefix" }
            return "${address.hostAddress}/$length"
        }
        private fun field(obj: JsonObject, key: String): String = optionalField(obj, key) ?: error("Missing WARP field")
        private fun optionalField(obj: JsonObject, key: String): String? {
            val value = obj.get(key) ?: return null
            if (value.isJsonNull) return null
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Invalid WARP field" }
            return value.asString.takeIf { it.isNotBlank() }
        }
    }
}
