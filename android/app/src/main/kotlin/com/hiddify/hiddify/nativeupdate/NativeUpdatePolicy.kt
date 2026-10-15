package com.hiddify.hiddify.nativeupdate

import com.hiddify.hiddify.nativecore.NativeReleasePolicy
import com.hiddify.hiddify.nativeprofile.NativeJsonDocument
import java.time.Instant

enum class NativeUpdateChannel { STABLE, DEV }
data class NativeAppRelease(
    val channel: NativeUpdateChannel, val version: String, val buildNumber: Int, val tag: String,
    val pageUrl: String, val assetId: Long, val assetName: String, val url: String,
    val size: Long, val sha256: String, val publishedAt: Long,
) {
    val identity: String get() = "${channel.name}:$assetId:$sha256"
}

internal object NativeUpdatePolicy {
    const val MAX_APK_BYTES = 300L * 1024 * 1024
    const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
    private val sha = Regex("[a-f0-9]{64}")
    private fun code(version: String): Int? = runCatching {
        val v = version.split('.').map(String::toLong)
        require(v.size == 3 && v[1] in 0..99 && v[2] in 0..99)
        (v[0] * 10000 + v[1] * 100 + v[2]).takeIf { it in 1..2100000000 }?.toInt()
    }.getOrNull()

    fun valid(release: NativeAppRelease): Boolean =
        release.assetId > 0 && code(release.version) != null && release.buildNumber in 1..2100000000 &&
            release.size in 1..MAX_APK_BYTES && sha.matches(release.sha256) &&
            release.assetName.matches(Regex("[A-Za-z0-9._-]+\\.apk")) && release.assetName.contains("arm64", true) &&
            NativeReleasePolicy.trustedUrl(release.pageUrl, "/SawaMEN/hiddify-app/releases/") &&
            release.url == "https://github.com/SawaMEN/hiddify-app/releases/download/${release.tag}/${release.assetName}" &&
            ((release.channel == NativeUpdateChannel.STABLE && NativeReleasePolicy.productionTag(release.tag)?.let {
                it.first == release.version && (it.second == 0 || it.second == release.buildNumber)
            } == true) ||
                (release.channel == NativeUpdateChannel.DEV && release.tag == "dev-latest"))

    fun releases(json: String, channel: NativeUpdateChannel): List<NativeAppRelease> {
        val root = NativeJsonDocument.parse(json).asJsonArray
        require(root.size() <= 100)
        return root.mapNotNull { item -> runCatching {
            val r = item.asJsonObject
            if (r.get("draft")?.asBoolean == true) return@runCatching null
            val tag = r.get("tag_name").asString
            val version = if (channel == NativeUpdateChannel.STABLE) {
                if (r.get("prerelease")?.asBoolean == true) return@runCatching null
                NativeReleasePolicy.productionTag(tag)?.first ?: return@runCatching null
            } else {
                if (tag != "dev-latest") return@runCatching null
                Regex("\\b(\\d+\\.\\d+\\.\\d+)\\b").find(r.get("name").asString)?.groupValues?.get(1) ?: return@runCatching null
            }
            val asset = r.getAsJsonArray("assets").firstOrNull {
                val name = it.asJsonObject.get("name").asString
                name.endsWith(".apk") && name.contains("arm64", true) &&
                    (channel == NativeUpdateChannel.DEV || !name.contains("dev", true))
            }?.asJsonObject ?: return@runCatching null
            val taggedBuild = if (channel == NativeUpdateChannel.STABLE) NativeReleasePolicy.productionTag(tag)?.second else null
            val build = taggedBuild?.takeIf { it > 0 } ?: code(version) ?: return@runCatching null
            val candidate = NativeAppRelease(channel, version, build, tag,
                r.get("html_url").asString, asset.get("id").asLong, asset.get("name").asString,
                asset.get("browser_download_url").asString, asset.get("size").asLong,
                asset.get("digest")?.takeUnless { it.isJsonNull }?.asString?.removePrefix("sha256:").orEmpty(),
                Instant.parse(asset.get("updated_at").asString).toEpochMilli())
            candidate.takeIf(::valid)
        }.getOrNull() }.sortedWith(compareByDescending<NativeAppRelease> { it.buildNumber }.thenByDescending { it.publishedAt })
    }

    fun newer(remote: NativeAppRelease, currentBuild: Int, builtAt: Long, installedSha256: String): Boolean =
        remote.buildNumber > currentBuild || (remote.buildNumber == currentBuild && remote.channel == NativeUpdateChannel.DEV &&
            sha.matches(installedSha256) && remote.sha256 != installedSha256 && remote.publishedAt > builtAt)

    fun due(now: Long, lastCheck: Long): Boolean = lastCheck <= 0 || now < lastCheck || now - lastCheck >= CHECK_INTERVAL_MS
    fun notify(identity: String, skipped: String?, notified: String?): Boolean = identity != skipped && identity != notified
}
