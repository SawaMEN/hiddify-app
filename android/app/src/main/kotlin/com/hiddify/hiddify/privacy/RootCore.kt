package com.hiddify.hiddify.privacy

import android.content.Context
import com.hiddify.hiddify.Settings
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/** A root manager grants su. Only our packaged companion is executed; no shell profiles or downloads. */
object RootCore {
    private var process: Process? = null
    private fun quote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"

    fun check(context: Context): Map<String, Any> {
        val helper = File(context.applicationInfo.nativeLibraryDir, "libhiddify-root.so")
        val child = ProcessBuilder("su", "-c", "id -u").redirectErrorStream(true).start()
        return try {
            check(child.waitFor(30, TimeUnit.SECONDS)) { "Root manager did not respond" }
            val granted = child.exitValue() == 0 && child.inputStream.bufferedReader().readText().trim() == "0"
            mapOf("granted" to granted, "helper" to helper.isFile, "tun" to (File("/dev/net/tun").exists() || File("/dev/tun").exists()))
        } finally { child.destroy() }
    }

    suspend fun start(context: Context) = withContext(Dispatchers.IO) {
        check(process == null) { "Root core is already running" }
        val helper = File(context.applicationInfo.nativeLibraryDir, "libhiddify-root.so")
        check(helper.isFile) { "This APK has no root companion" }
        val directory = File(context.filesDir, "root-core").apply { mkdirs() }
        File(directory, "tmp").mkdirs()
        val options = JSONObject(Settings.configOptions.ifBlank { "{}" })
        val region = options.optString("region", "other")
        RegionalRouting.policy(context, region).forEach { (key, value) ->
            options.put(key, if (value is List<*>) org.json.JSONArray(value) else value)
        }
        options.put("enable-tun", true).put("privacy-root", true)
        options.put("privacy-full-tunnel", Settings.privacyFullTunnel)
        options.put("privacy-hide-local-proxy", Settings.privacyHideLocalProxy)
        if (Settings.privacyHideClashApi) options.put("enable-clash-api", false)
        if (Settings.privacyDisableSystemProxy) options.put("set-system-proxy", false)
        if (Settings.privacyEncryptedDns) options.put("remote-dns-address", "https://1.1.1.1/dns-query")
        if (Settings.privacyFullTunnel) options.put("strict-route", true).put("bypass-lan", false)
        val envelope = File(context.filesDir, "root-launch.json")
        envelope.writeText(JSONObject().put("base", directory.absolutePath)
            .put("port", Settings.grpcServiceModePort).put("secret", Settings.grpcAuthToken)
            .put("options", options).put("config", Settings.activeConfigPath)
            .put("autoStart", Settings.startCoreAfterStartingService).toString())
        envelope.setReadable(false, false); envelope.setReadable(true, true)
        envelope.setWritable(false, false); envelope.setWritable(true, true)
        val ready = CompletableDeferred<Unit>()
        val child = ProcessBuilder("su", "-c", "exec ${quote(helper.absolutePath)} ${quote(envelope.absolutePath)}")
            .redirectErrorStream(true).start()
        process = child
        Thread {
            try {
                child.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> if (line == "HC_ROOT_READY") ready.complete(Unit) }
                }
                if (!ready.isCompleted) ready.completeExceptionally(IllegalStateException("Root companion exited before initialization; check root permission and kernel support"))
            } catch (error: Exception) { if (!ready.isCompleted) ready.completeExceptionally(error) }
        }.apply { isDaemon = true; start() }
        try { withTimeout(30_000) { ready.await() } }
        catch (error: Exception) { stop(); throw error }
        finally { envelope.delete() }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        val child = process ?: return@withContext
        // Closing the control pipe requests Stop/Close in the root process. Never flush global firewall rules.
        runCatching { child.outputStream.close() }
        check(child.waitFor(15, TimeUnit.SECONDS)) { "Root core shutdown timed out; routing cleanup is not confirmed" }
        process = null
    }

    fun isAlive(): Boolean = process?.isAlive == true
}
