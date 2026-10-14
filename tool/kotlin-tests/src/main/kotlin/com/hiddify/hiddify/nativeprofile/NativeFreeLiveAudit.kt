package com.hiddify.hiddify.nativeprofile

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Opt-in live audit using the production parsers and registration requests. No secret logging. */
fun main() {
    val directory = Path.of(System.getenv("FREE_AUDIT_DIR") ?: error("FREE_AUDIT_DIR required"))
    Files.createDirectories(directory)
    val manifest = JsonArray()
    fun save(label: String, content: String, warp: Boolean = false) {
        val id = manifest.size().toString()
        Files.writeString(directory.resolve("$id.json"), content)
        val variants = JsonArray()
        if (warp) {
            val name = if (NativeJsonDocument.parse(content).asJsonObject.getAsJsonArray("endpoints")[0].asJsonObject.get("type").asString == "awg")
                "WARP + AmneziaWG" else "Cloudflare WARP"
            NativeWarpEndpoints.candidates(content, name).forEachIndexed { index, raw ->
                val file = "$id-$index.json"
                Files.writeString(directory.resolve(file), raw)
                variants.add(file)
            }
        }
        manifest.add(JsonObject().apply {
            addProperty("id", id); addProperty("label", label); addProperty("warp", warp); add("variants", variants)
        })
        println("RETRIEVAL $label: OK")
    }
    fun attempt(label: String, action: () -> Unit) {
        try { action() } catch (e: Exception) {
            // Only repository's sanitized details may be printed.
            println("RETRIEVAL $label: FAILED " + if (e is NativeWarpGenerationException) e.detail else e.javaClass.simpleName)
        }
    }
    val modes = listOf(NativeWarpMode.STANDARD, NativeWarpMode.AWG)
    for (mode in modes) attempt("Cloudflare $mode") {
        NativeWarpProfilesRepository().generate(NativeProfileImportCancellation(), mode) { raw ->
            save("Cloudflare $mode", raw, true)
        }
    }
    val http = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()
    for (request in NativeWarpProfilesRepository.defaultRequests) attempt(request.url.host) {
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP error" }
            val account = NativeWarpAccount.parse(response.body!!.byteStream().use(NativeProfileTransfer::readText))
            for (mode in modes) save("${request.url.host} $mode", account.content(mode), true)
        }
    }
    if (System.getenv("FREE_AUDIT_WARP_ONLY") != "1") {
    attempt("Opera") { save("Opera", NativeOperaProfilesRepository().generate()) }
    attempt("Free catalog") {
        http.newCall(Request.Builder().url(NativeFreeProfiles.FEED_URL).build()).execute().use { response ->
            check(response.isSuccessful)
            val catalog = NativeFreeProfiles.parse(response.body!!.string())
            for (profile in catalog) attempt(profile.titleEn) {
                http.newCall(Request.Builder().url(profile.url).build()).execute().use { feed ->
                    check(feed.isSuccessful)
                    save(profile.titleEn, feed.body!!.byteStream().use(NativeProfileTransfer::readText))
                }
            }
        }
    }
    }
    Files.writeString(directory.resolve("manifest.json"), manifest.toString())
}
