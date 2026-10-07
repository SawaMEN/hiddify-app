package com.hiddify.hiddify.sharing

/** Standard Wi-Fi QR payload. Escape delimiters in system-generated credentials. */
object HotspotWifiQr {
    private fun escape(value: String): String = buildString {
        value.forEach { character ->
            if (character in "\\;,:\"") append('\\')
            append(character)
        }
    }

    fun encode(ssid: String, password: String): String =
        "WIFI:T:WPA;S:${escape(ssid)};P:${escape(password)};;"
}
