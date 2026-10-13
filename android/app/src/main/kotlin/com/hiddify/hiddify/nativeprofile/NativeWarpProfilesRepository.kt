package com.hiddify.hiddify.nativeprofile

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

/** No credentials are logged and generator URLs are fixed locally, independent of the feed. */
internal class NativeWarpProfilesRepository(
    private val http: OkHttpClient = defaultHttp,
    private val requests: List<Request> = defaultRequests,
) {
    fun <T> generate(cancellation: NativeProfileImportCancellation, prepare: (String) -> T): T {
        for ((source, request) in requests.withIndex()) {
            cancellation.ensureActive()
            val call = http.newCall(request)
            cancellation.attachRequest(call::cancel)
            val content = try {
                call.execute().use { response ->
                    check(response.isSuccessful) { "WARP generator unavailable" }
                    val body = response.body ?: error("Empty WARP generator response")
                    body.byteStream().use { NativeWarpProfiles.generatedContent(NativeProfileTransfer.readText(it), source) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                cancellation.ensureActive()
                null
            } finally {
                cancellation.detachRequest()
            }
            if (content != null) {
                try {
                    cancellation.ensureActive()
                    return prepare(content)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    cancellation.ensureActive()
                    // Core rejection is also a failed source; try the next one without persisting.
                }
            }
        }
        throw NativeWarpGenerationException()
    }

    companion object {
        private val defaultHttp = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
        private val json = "application/json; charset=utf-8".toMediaType()
        private val defaultRequests = listOf(
            Request.Builder().url("https://warp.llimonix.pw/api/warp")
                .header("Origin", "https://warp.llimonix.pw").header("Referer", "https://warp.llimonix.pw/")
                .post("""{"selectedServices":[],"siteMode":"all","deviceType":"computer"}""".toRequestBody(json)).build(),
            Request.Builder().url("https://topor-warp.vercel.app/generate")
                .header("Origin", "https://topor-warp.vercel.app").header("Referer", "https://topor-warp.vercel.app/")
                .post("""{"platform":"all"}""".toRequestBody(json)).build(),
            Request.Builder().url("https://warp-gen.vercel.app/generate-config")
                .header("Referer", "https://warp-gen.vercel.app/").build(),
            Request.Builder().url("https://config-generator-warp.vercel.app/warp")
                .header("Referer", "https://config-generator-warp.vercel.app/").build(),
        )
    }
}

internal class NativeWarpGenerationException : Exception("WARP generators are unavailable")
