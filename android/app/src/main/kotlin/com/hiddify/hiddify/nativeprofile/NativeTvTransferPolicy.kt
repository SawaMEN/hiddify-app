package com.hiddify.hiddify.nativeprofile

import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI

/** Fail-closed policy for TV's short-lived LAN-only subscription transfer form. */
internal object NativeTvTransferPolicy {
    const val MAX_LINK_BYTES = 4096
    const val MAX_FORM_BYTES = 8192
    const val MAX_HEADER_BYTES = 4096
    const val MAX_WRONG_CODES = 10

    fun isPrivateIpv4(address: InetAddress): Boolean {
        if (address !is Inet4Address) return false
        val b = address.address.map { it.toInt() and 0xff }
        return b[0] == 10 || (b[0] == 172 && b[1] in 16..31) ||
            (b[0] == 192 && b[1] == 168)
    }

    fun sameSubnet(server: InetAddress, peer: InetAddress, prefixLength: Int): Boolean {
        if (!isPrivateIpv4(server) || !isPrivateIpv4(peer) || prefixLength !in 8..32) return false
        val a = server.address
        val b = peer.address
        val fullBytes = prefixLength / 8
        for (i in 0 until fullBytes) if (a[i] != b[i]) return false
        val remaining = prefixLength % 8
        if (remaining > 0) {
            val mask = (0xff shl (8 - remaining)) and 0xff
            if ((a[fullBytes].toInt() and mask) != (b[fullBytes].toInt() and mask)) return false
        }
        return true
    }

    fun matchesHost(header: String?, address: String, port: Int): Boolean =
        port in 1..65535 && header?.trim() == address + ":" + port

    fun validSubscriptionUrl(raw: String): String? {
        val value = raw.trim()
        if (value.isBlank() || value.toByteArray(Charsets.UTF_8).size > MAX_LINK_BYTES ||
            value.any { it.isWhitespace() || it.isISOControl() }) return null
        val url = runCatching { URI(value) }.getOrNull() ?: return null
        if (url.scheme?.lowercase() !in setOf("http", "https") || url.host.isNullOrBlank() ||
            url.rawUserInfo != null || url.rawFragment != null || url.port == 0 || url.port > 65535) return null
        return value
    }
}
