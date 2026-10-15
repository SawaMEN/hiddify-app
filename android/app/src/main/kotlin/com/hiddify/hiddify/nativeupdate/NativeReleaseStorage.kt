package com.hiddify.hiddify.nativeupdate

import com.google.gson.JsonObject
import com.hiddify.hiddify.nativeprofile.NativeJsonDocument

/** Explicit preference schema: survives R8 renaming fields and updating the app's bytecode. */
internal object NativeReleaseStorage {
    fun encode(release: NativeAppRelease): String {
        require(NativeUpdatePolicy.valid(release))
        return JsonObject().apply {
            addProperty("channel", release.channel.name)
            addProperty("version", release.version)
            addProperty("buildNumber", release.buildNumber)
            addProperty("tag", release.tag)
            addProperty("pageUrl", release.pageUrl)
            addProperty("assetId", release.assetId)
            addProperty("assetName", release.assetName)
            addProperty("url", release.url)
            addProperty("size", release.size)
            addProperty("sha256", release.sha256)
            addProperty("publishedAt", release.publishedAt)
        }.toString()
    }

    fun decode(json: String): NativeAppRelease {
        val r = NativeJsonDocument.parse(json).asJsonObject
        return NativeAppRelease(
            NativeUpdateChannel.valueOf(r.get("channel").asString), r.get("version").asString,
            r.get("buildNumber").asInt, r.get("tag").asString, r.get("pageUrl").asString,
            r.get("assetId").asLong, r.get("assetName").asString, r.get("url").asString,
            r.get("size").asLong, r.get("sha256").asString, r.get("publishedAt").asLong,
        ).also { require(NativeUpdatePolicy.valid(it)) }
    }
}
