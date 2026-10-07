package com.hiddify.hiddify.nativeprofile

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class NativeFreeProfilesRepository {
    suspend fun load(): List<NativeFreeProfile> = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(Request.Builder().url(NativeFreeProfiles.FEED_URL).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val profiles = response.use {
                        check(it.isSuccessful) { "Free profile feed: HTTP ${it.code}" }
                        val body = it.body ?: error("Empty free profile feed")
                        body.byteStream().use { input -> NativeFreeProfiles.parse(NativeProfileTransfer.readText(input)) }
                    }
                    if (continuation.isActive) continuation.resume(profiles)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }
    companion object {
        private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    }
}
