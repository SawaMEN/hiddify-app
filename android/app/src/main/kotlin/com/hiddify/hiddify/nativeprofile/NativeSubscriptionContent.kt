package com.hiddify.hiddify.nativeprofile

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

/** Decode subscription envelopes once, before inspecting their links and metadata. */
internal object NativeSubscriptionContent {
    fun utf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes))
            .toString().removePrefix("\uFEFF")
    } catch (error: java.nio.charset.CharacterCodingException) {
        throw IllegalArgumentException("Configuration is not valid UTF-8", error)
    }

    fun decode(value: String): String {
        val original = value.removePrefix("\uFEFF")
        if (':' in original || original.trimStart().startsWith('[') || original.trimStart().startsWith('{')) return original
        val compact = original.filterNot(Char::isWhitespace)
        if (compact.isEmpty() || compact.any { !it.isLetterOrDigit() && it !in "+/-_=" }) return original
        return runCatching {
            utf8(Base64.getDecoder().decode(compact.replace('-', '+').replace('_', '/')))
        }.getOrDefault(original)
    }

    fun expand(raw: String, isUrl: (String) -> Boolean, download: (String) -> String,
        ensureActive: () -> Unit = {}): String {
        val content = decode(raw)
        val lines = content.split('\n')
        require(lines.count { isUrl(it.trim()) } <= 128) { "Too many nested subscriptions" }
        var bytes = 0L
        val result = StringBuilder()
        for ((index, original) in lines.withIndex()) {
            ensureActive()
            val line = original.trim()
            val replacement = if (isUrl(line)) decode(download(line)).trim() else original
            bytes += replacement.toByteArray(Charsets.UTF_8).size.toLong() + if (index == 0) 0 else 1
            require(bytes <= NativeProfileTransfer.MAX_CONFIG_BYTES) { "Nested subscriptions exceed 8 MiB" }
            if (index != 0) result.append('\n')
            result.append(replacement)
        }
        return result.toString()
    }
}
