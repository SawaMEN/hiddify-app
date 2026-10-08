package com.hiddify.hiddify.privacy

import android.content.Context
import android.util.AtomicFile
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.hiddify.hiddify.nativeprofile.NativeProfileTransfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class NativeCommunityCatalogue(val services: List<NativeCommunityService>, val cached: Boolean)

class NativeCommunityRepository(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "community-catalogues")

    suspend fun load(source: NativeCommunitySource, onCached: (NativeCommunityCatalogue) -> Unit): NativeCommunityCatalogue {
        val cache = AtomicFile(File(directory, "${source.name.lowercase()}.json"))
        val saved = withContext(Dispatchers.IO) {
            runCatching { cache.openRead().use { NativeCommunityLists.parse(NativeProfileTransfer.readText(it)) } }.getOrNull()
        }
        if (saved != null) onCached(NativeCommunityCatalogue(saved, true))
        return suspendCancellableCoroutine { continuation ->
            val call = http.newCall(Request.Builder().url(source.catalogueUrl).build())
            continuation.invokeOnCancellation { call.cancel() }
            fun fail(error: Exception) {
                if (!continuation.isActive) return
                if (saved != null) continuation.resume(NativeCommunityCatalogue(saved, true))
                else continuation.resumeWithException(error)
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) = fail(error)
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val services = response.use {
                            check(it.isSuccessful) { "Community catalogue: HTTP ${it.code}" }
                            val body = it.body ?: error("Empty community catalogue")
                            body.byteStream().use { input -> NativeCommunityLists.parse(NativeProfileTransfer.readText(input)) }
                        }
                        if (!continuation.isActive) return
                        // Cache only compact, validated metadata; an error response never replaces it.
                        val document = JsonObject()
                        services.forEach { service -> document.add(service.id, JsonArray().apply {
                            service.domains.forEach { add(it) }
                        }) }
                        runCatching {
                            directory.mkdirs()
                            val output = cache.startWrite()
                            try {
                                output.write(document.toString().toByteArray(Charsets.UTF_8))
                                cache.finishWrite(output)
                            } catch (error: Exception) { cache.failWrite(output); throw error }
                        }
                        if (continuation.isActive) continuation.resume(NativeCommunityCatalogue(services, false))
                    } catch (error: Exception) { fail(error) }
                }
            })
        }
    }

    companion object {
        private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    }
}
