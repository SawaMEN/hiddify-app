package com.hiddify.hiddify.nativeprofile

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI

/** Shared limits for document imports, editors and raw profile exports. */
object NativeProfileTransfer {
    const val MAX_CONFIG_BYTES = 8 * 1024 * 1024
    const val MAX_CLIPBOARD_BYTES = 256 * 1024

    fun readText(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            require(total <= MAX_CONFIG_BYTES) { "Configuration exceeds 8 MiB" }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
    }

    // Preserve the original authority, encoded query and non-standard port, just as the Dart
    // share action does. Construct only the fragment so tokens are never decoded/re-encoded.
    fun subscriptionLink(url: String, name: String): String {
        val normalized = url.trim()
        val uri = URI.create(normalized)
        require((uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
            !uri.host.isNullOrBlank() && (uri.port == -1 || uri.port in 1..65535)) { "Invalid subscription URL" }
        val fragment = URI(null, null, name).toASCIIString()
        return normalized.substringBefore('#') + fragment
    }
}
