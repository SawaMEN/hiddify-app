package com.hiddify.hiddify.nativecore

import java.net.URI

/** Dart's SOCKS sharing URI, including encoded credentials and IPv6 authorities. */
object NativeLanSharingLink {
    fun create(host: String, port: Int, password: String): String {
        val ip = host.trim().removeSurrounding("[", "]")
        require(ip.isNotEmpty() && ip.none { it.isWhitespace() || it.isISOControl() || it in "@/?#\\[]" }) {
            "Invalid LAN address"
        }
        val resolvedPort = if (port <= 0) 12334 else port
        require(resolvedPort in 1..65535) { "Invalid LAN port" }
        val address = if (':' in ip) "[$ip]" else ip
        require(URI("socks://$address:$resolvedPort").host != null) { "Invalid LAN address" }
        val credentials = if (password.isEmpty()) "" else "hiddify:${encodeComponent(password)}@"
        return "socks://$credentials$address:$resolvedPort"
    }

    private fun encodeComponent(value: String): String = buildString {
        val hex = "0123456789ABCDEF"
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val code = byte.toInt() and 255
            val char = code.toChar()
            if (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in "-_.!~*'()") append(char)
            else { append('%'); append(hex[code shr 4]); append(hex[code and 15]) }
        }
    }
}
