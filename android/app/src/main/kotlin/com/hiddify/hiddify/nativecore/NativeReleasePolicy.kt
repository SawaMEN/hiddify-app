package com.hiddify.hiddify.nativecore

import java.net.URI

/** Exact fork production tags; prerelease suffixes must never become stable releases. */
internal object NativeReleasePolicy {
    private val production = Regex("v?([0-9]+\\.[0-9]+\\.[0-9]+)(?:\\.prod)?(?:\\+([0-9]+)(?:\\.prod)?)?")
    fun productionTag(tag: String): Pair<String, Int>? {
        val match = production.matchEntire(tag) ?: return null
        val version = match.groupValues[1]
        if (version.split('.').any { it.toIntOrNull() == null }) return null
        val buildText = match.groupValues[2]
        val build = if (buildText.isEmpty()) 0 else buildText.toIntOrNull() ?: return null
        return version to build
    }
    fun trustedUrl(value: String, pathPrefix: String): Boolean = runCatching {
        val uri = URI(value)
        val decodedPath = URI(null, null, uri.path, null).normalize().path
        uri.scheme.equals("https", true) && uri.host.equals("github.com", true) &&
            uri.rawUserInfo == null && uri.port in listOf(-1, 443) && decodedPath.startsWith(pathPrefix)
    }.getOrDefault(false)
}
