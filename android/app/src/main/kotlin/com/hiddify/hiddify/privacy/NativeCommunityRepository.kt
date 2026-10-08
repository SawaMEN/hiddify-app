package com.hiddify.hiddify.privacy

import android.content.Context
import android.util.AtomicFile
import com.hiddify.hiddify.nativeprofile.NativeProfileTransfer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

data class NativeCommunityCatalogue(val services: List<NativeCommunityService>, val cached: Boolean,
    val groupsUnavailable: Boolean = false)

class NativeCommunityRepository(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "community-catalogues")

    suspend fun load(source: NativeCommunitySource, onCached: (NativeCommunityCatalogue) -> Unit): NativeCommunityCatalogue {
        val cache = AtomicFile(File(directory, "${source.name.lowercase()}.json"))
        val saved = withContext(Dispatchers.IO) {
            runCatching { cache.openRead().use { NativeCommunityLists.parse(NativeProfileTransfer.readText(it)) } }.getOrNull()
        }
        if (saved != null) onCached(NativeCommunityCatalogue(saved, true))
        val fresh = try {
            coroutineScope {
                val services = async(Dispatchers.IO) { NativeCommunityLists.parse(download(source.catalogueUrl)) }
                val groups = async(Dispatchers.IO) {
                    try { NativeCommunityLists.parseGroups(download(source.groupsUrl)) to false }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { saved.orEmpty().associate { it.id to it.group } to true }
                }
                val (categories, unavailable) = groups.await()
                NativeCommunityCatalogue(NativeCommunityLists.withGroups(services.await(), categories), false, unavailable)
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (saved != null) return NativeCommunityCatalogue(saved, true)
            throw error
        }
        withContext(Dispatchers.IO) {
            // Both compact metadata feeds are cached together; old array-only caches still load.
            runCatching {
                directory.mkdirs()
                val output = cache.startWrite()
                try {
                    output.write(NativeCommunityLists.encodeCache(fresh.services).toByteArray(Charsets.UTF_8))
                    cache.finishWrite(output)
                } catch (error: Exception) { cache.failWrite(output); throw error }
            }
        }
        return fresh
    }

    private suspend fun download(url: String): String = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use {
                        check(it.isSuccessful) { "Community catalogue: HTTP ${it.code}" }
                        val body = it.body ?: error("Empty community catalogue")
                        body.byteStream().use(NativeProfileTransfer::readText)
                    }
                    if (continuation.isActive) continuation.resume(text)
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
