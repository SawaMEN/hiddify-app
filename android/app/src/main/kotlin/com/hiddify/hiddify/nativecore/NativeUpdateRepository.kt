package com.hiddify.hiddify.nativecore

import com.hiddify.hiddify.nativeupdate.NativeAppRelease
import com.hiddify.hiddify.nativeupdate.NativeUpdateChannel
import com.hiddify.hiddify.nativeupdate.NativeUpdatePolicy
import java.net.HttpURLConnection
import java.net.URL

internal class NativeUpdateRepository {
    companion object {
        const val FORK_URL = "https://github.com/SawaMEN/hiddify-app"
        const val UPSTREAM_URL = "https://github.com/hiddify/hiddify-app"
        const val TERMS_URL = "$FORK_URL/blob/main/docs/TERMS.md"
        const val PRIVACY_URL = "$FORK_URL/blob/main/docs/PRIVACY.md"
    }

    fun latestCompatible(channel: NativeUpdateChannel): NativeAppRelease? {
        val json = get("https://api.github.com/repos/SawaMEN/hiddify-app/releases?per_page=100")
        val release = NativeUpdatePolicy.releases(json, channel).firstOrNull() ?: return null
        // New publications carry the exact APK build code/commit. Old stable releases still work.
        val root = com.hiddify.hiddify.nativeprofile.NativeJsonDocument.parse(json).asJsonArray
        val metadata = root.first { it.asJsonObject.get("tag_name").asString == release.tag }.asJsonObject
            .getAsJsonArray("assets").firstOrNull { it.asJsonObject.get("name").asString == "update.json" }?.asJsonObject
        return if (metadata == null) release else {
            val url = metadata.get("browser_download_url").asString
            require(url == "$FORK_URL/releases/download/${release.tag}/update.json")
            NativeUpdatePolicy.manifest(release, get(url))
        }
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "VetrOFF-Android")
        }
        try {
            check(connection.responseCode in 200..299) { "GitHub HTTP ${connection.responseCode}" }
            return connection.inputStream.use {
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = it.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 2 * 1024 * 1024) { "Update feed is too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray().toString(Charsets.UTF_8)
            }
        } finally { connection.disconnect() }
    }
}
