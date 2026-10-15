package com.hiddify.hiddify.nativeupdate

import java.io.File
import java.security.MessageDigest

internal object NativeUpdateArtifact {
    fun matches(file: File, release: NativeAppRelease): Boolean {
        if (!NativeUpdatePolicy.valid(release) || !file.isFile || file.length() != release.size) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(64 * 1024)
            while (true) { val count = input.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == release.sha256
    }
}
