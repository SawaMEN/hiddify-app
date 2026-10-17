package com.hiddify.hiddify.nativeprofile

/**
 * olcRTC v1 link convention. This is NOT a java.net.URI: '#' is a secret
 * encryption key, not a display name. Parsing is independent of native support.
 */
internal class OlcRtcShareLink private constructor(
    val provider: String,
    val transport: String,
    val roomId: String,
    val encryptionKey: String,
    val transportParameters: Map<String, String>,
    val comment: String?,
) {
    internal fun fingerprint(): String = buildString {
        append(provider).append('|').append(transport).append('|').append(roomId)
        append('|').append(encryptionKey)
        transportParameters.toSortedMap().forEach { (key, value) ->
            append('|').append(key).append('=').append(value)
        }
    }

    override fun toString(): String =
        "OlcRtcShareLink(provider=" + provider + ", transport=" + transport + ", credentials=<redacted>)"

    companion object {
        private const val PREFIX = "olcrtc://"
        private const val MAX_LINK_LENGTH = 16_384
        private val providers = setOf("jitsi", "telemost", "wbstream")
        private val transports = setOf("datachannel", "vp8channel", "seichannel", "videochannel")
        private val allowedParameters = mapOf(
            "datachannel" to emptySet(),
            "vp8channel" to setOf("vp8-fps", "vp8-batch"),
            "seichannel" to setOf("fps", "batch", "frag", "ack-ms"),
            "videochannel" to setOf(
                "video-w", "video-h", "video-fps", "video-codec", "video-qr-size",
                "video-qr-recovery", "video-tile-module", "video-tile-rs",
            ),
        )
        private val numericParameters = setOf(
            "vp8-fps", "vp8-batch", "fps", "batch", "frag", "ack-ms", "video-w",
            "video-h", "video-fps", "video-qr-size", "video-tile-module", "video-tile-rs",
        )

        fun isOlcRtc(text: String): Boolean = text.trimStart().startsWith(PREFIX, ignoreCase = true)

        fun parse(text: String): OlcRtcShareLink {
            val link = text.trim()
            require(link.length <= MAX_LINK_LENGTH && link.startsWith(PREFIX, ignoreCase = true)) {
                "Invalid olcRTC share link"
            }
            require(link.none { it == '\n' || it == '\r' || it.code < 0x20 }) {
                "Invalid olcRTC share link"
            }
            val payload = link.substring(PREFIX.length)
            val keySeparator = payload.lastIndexOf('#')
            require(keySeparator > 0) { "olcRTC link requires an encryption key" }
            val beforeKey = payload.substring(0, keySeparator)
            val keyAndComment = payload.substring(keySeparator + 1)
            val key = keyAndComment.substringBefore('$')
            require(key.length == 64 && key.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                "olcRTC key must contain 64 hexadecimal characters"
            }
            val comment = keyAndComment.substringAfter('$', "").trim().takeIf(String::isNotEmpty)
                ?.also { require(it.length <= 256) { "olcRTC label is too long" } }
            // A room URL can contain '@'; the first '@' separates the transport header.
            val at = beforeKey.indexOf('@')
            require(at > 0 && at < beforeKey.lastIndex) { "olcRTC link requires a room ID" }
            val header = beforeKey.substring(0, at)
            val room = beforeKey.substring(at + 1)
            require(room.isNotBlank() && room.length <= 2048) { "Invalid olcRTC room ID" }
            val question = header.indexOf('?')
            require(question > 0 && question < header.lastIndex) { "olcRTC link requires a transport" }
            val provider = header.substring(0, question).lowercase()
            require(provider in providers) { "Unsupported olcRTC provider" }
            val transportSpec = header.substring(question + 1)
            val transport = transportSpec.substringBefore('<').lowercase()
            require(transport in transports) { "Unsupported olcRTC transport" }
            require(!(provider == "telemost" && transport in setOf("datachannel", "seichannel"))) {
                "Telemost cannot use this olcRTC transport"
            }
            require(!(provider == "wbstream" && transport == "datachannel")) {
                "WB Stream DataChannel requires a moderator token; use another transport"
            }
            val parameters = linkedMapOf<String, String>()
            if ('<' in transportSpec) {
                require(transportSpec.endsWith('>') && transportSpec.count { it == '<' } == 1 && transportSpec.count { it == '>' } == 1) {
                    "Invalid olcRTC transport parameters"
                }
                val rawParameters = transportSpec.substringAfter('<').dropLast(1)
                require(rawParameters.isNotEmpty()) { "Invalid olcRTC transport parameters" }
                for (part in rawParameters.split('&')) {
                    val separator = part.indexOf('=')
                    require(separator > 0 && separator < part.lastIndex) { "Invalid olcRTC transport parameter" }
                    val name = part.substring(0, separator)
                    val value = part.substring(separator + 1)
                    require(name in allowedParameters.getValue(transport) && name !in parameters) {
                        "Unsupported or duplicate olcRTC transport parameter"
                    }
                    require(value.length <= 64 && value.none(Char::isWhitespace)) {
                        "Invalid olcRTC transport parameter value"
                    }
                    if (name in numericParameters) {
                        val number = value.toIntOrNull()
                        require(number != null && number >= (if (name == "video-tile-rs" || name == "video-qr-size") 0 else 1) && number <= 1_000_000) {
                            "Invalid numeric olcRTC transport parameter"
                        }
                    }
                    parameters[name] = value
                }
            }
            return OlcRtcShareLink(provider, transport, room, key.lowercase(), parameters, comment)
        }
    }
}
