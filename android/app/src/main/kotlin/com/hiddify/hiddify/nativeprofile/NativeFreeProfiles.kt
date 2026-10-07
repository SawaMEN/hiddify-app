package com.hiddify.hiddify.nativeprofile

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.net.URI

/** The Dart free_configs schema; absent needed_features differs from an empty feature list. */
data class NativeFreeProfile(
    val regions: List<String>, val titleEn: String, val titleRu: String?, val url: String,
    val tagsEn: List<String>, val tagsRu: List<String>?, val consentEn: String, val consentRu: String?,
    val neededFeatures: Set<String>?,
) {
    fun title(russian: Boolean) = if (russian) titleRu ?: titleEn else titleEn
    fun tags(russian: Boolean) = if (russian) tagsRu ?: tagsEn else tagsEn
    fun consent(russian: Boolean) = if (russian) consentRu ?: consentEn else consentEn
    fun matches(region: String) = regions.isEmpty() || region in regions
}

object NativeFreeProfiles {
    const val FEED_URL = "https://raw.githubusercontent.com/hiddify/hiddify-app/refs/heads/main/test.configs/free_configs"

    fun parse(text: String): List<NativeFreeProfile> {
        val root = NativeJsonDocument.parse(text).asJsonObject
        val entries = root.get("profiles")
        require(entries != null && entries.isJsonArray) { "Missing free profiles list" }
        return entries.asJsonArray.map { element ->
            val item = element.asJsonObject
            val title = item.getAsJsonObject("title")
            val consent = item.getAsJsonObject("consent")
            val tags = item.getAsJsonObject("tags")
            val url = string(item, "sublink")!!
            val uri = URI(url)
            require(uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank() && (uri.port == -1 || uri.port in 1..65535)) { "Invalid free subscription URL" }
            NativeFreeProfile(strings(item.get("region"))!!, string(title, "en")!!, string(title, "ru", false), url,
                strings(tags.get("en"))!!, strings(tags.get("ru"), false), string(consent, "en")!!, string(consent, "ru", false),
                strings(item.get("needed_features"), false)?.toSet())
        }
    }

    private fun string(owner: JsonObject, key: String, required: Boolean = true): String? {
        val value = owner.get(key)
        if (!required && (value == null || value.isJsonNull)) return null
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Invalid $key" }
        return value.asString
    }
    private fun strings(value: JsonElement?, required: Boolean = true): List<String>? {
        if (!required && (value == null || value.isJsonNull)) return null
        require(value != null && value.isJsonArray) { "Invalid string list" }
        return value.asJsonArray.map { require(it.isJsonPrimitive && it.asJsonPrimitive.isString); it.asString }
    }
}
