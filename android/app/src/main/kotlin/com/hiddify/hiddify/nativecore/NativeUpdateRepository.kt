package com.hiddify.hiddify.nativecore

import org.json.JSONArray
import java.net.HttpURLConnection
import com.hiddify.hiddify.nativeprofile.NativeProfileTransfer
import java.net.URL

data class NativeReleaseInfo(
    val version: String,
    val buildNumber: Int,
    val tag: String,
    val pageUrl: String,
)

class NativeUpdateRepository {
    companion object {
        const val FORK_URL = "https://github.com/SawaMEN/hiddify-app"
        const val UPSTREAM_URL = "https://github.com/hiddify/hiddify-app"
        const val TERMS_URL = "https://github.com/SawaMEN/hiddify-app/blob/main/docs/TERMS.md"
        const val PRIVACY_URL = "https://github.com/SawaMEN/hiddify-app/blob/main/docs/PRIVACY.md"
        private const val RELEASES_API = "https://api.github.com/repos/SawaMEN/hiddify-app/releases"
    }

    fun latestCompatible(): NativeReleaseInfo? {
        val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "VetrOFF-Android")
        }

        try {
            check(connection.responseCode in 200..299) {
                "GitHub returned HTTP ${connection.responseCode}"
            }
            val body = connection.inputStream.use(NativeProfileTransfer::readText)
            val releases = JSONArray(body)
            var best: NativeReleaseInfo? = null

            for (index in 0 until releases.length()) {
                val release = releases.optJSONObject(index) ?: continue
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue

                val tag = release.optString("tag_name").trim()
                val parsed = parseProductionTag(tag) ?: continue
                val pageUrl = release.optString("html_url").trim()
                if (!isTrustedReleasePage(pageUrl)) continue

                val assets = release.optJSONArray("assets") ?: continue
                var compatibleAsset = false
                for (assetIndex in 0 until assets.length()) {
                    val asset = assets.optJSONObject(assetIndex) ?: continue
                    val name = asset.optString("name").lowercase()
                    val url = asset.optString("browser_download_url")
                    if (name.endsWith(".apk") && name.contains("arm64") && isTrustedAsset(url)) {
                        compatibleAsset = true
                        break
                    }
                }
                if (!compatibleAsset) continue

                val candidate =
                    NativeReleaseInfo(
                        version = parsed.first,
                        buildNumber = parsed.second,
                        tag = tag,
                        pageUrl = pageUrl,
                    )
                if (best == null || compare(candidate, best) > 0) best = candidate
            }
            return best
        } finally {
            connection.disconnect()
        }
    }

    fun isNewer(
        remote: NativeReleaseInfo,
        currentVersion: String,
        currentBuild: Int,
    ): Boolean {
        val versionComparison = compareVersions(remote.version, currentVersion)
        return versionComparison > 0 ||
            (versionComparison == 0 && remote.buildNumber > currentBuild)
    }

    private fun parseProductionTag(tag: String): Pair<String, Int>? = NativeReleasePolicy.productionTag(tag)

    private fun compare(
        left: NativeReleaseInfo,
        right: NativeReleaseInfo,
    ): Int {
        val version = compareVersions(left.version, right.version)
        return if (version != 0) version else left.buildNumber.compareTo(right.buildNumber)
    }

    private fun compareVersions(
        left: String,
        right: String,
    ): Int {
        val a = left.split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.substringBefore('-').substringBefore('+').split('.').map { it.toIntOrNull() ?: 0 }
        val size = maxOf(a.size, b.size)
        for (index in 0 until size) {
            val comparison = a.getOrElse(index) { 0 }.compareTo(b.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun isTrustedReleasePage(value: String): Boolean =
        trustedUri(value, "/SawaMEN/hiddify-app/releases/")

    private fun isTrustedAsset(value: String): Boolean =
        trustedUri(value, "/SawaMEN/hiddify-app/releases/download/")

    private fun trustedUri(value: String, pathPrefix: String): Boolean = NativeReleasePolicy.trustedUrl(value, pathPrefix)
}
