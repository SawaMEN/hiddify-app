package com.hiddify.hiddify.privacy

import android.content.Context
import com.hiddify.hiddify.Settings
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/** A root manager grants su. Only our packaged companion is executed; no shell profiles or downloads. */
object RootCore {
    @Volatile private var process: Process? = null
    private fun quote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun alive(child: Process): Boolean = try { child.exitValue(); false } catch (_: IllegalThreadStateException) { true }
    private fun awaitExit(child: Process, timeoutMs: Long): Boolean {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (alive(child)) {
            if (android.os.SystemClock.elapsedRealtime() >= deadline) return false
            Thread.sleep(50)
        }
        return true
    }

    fun detect(context: Context): Map<String, Any> {
        val helper = File(context.applicationInfo.nativeLibraryDir, "libhiddify-root.so")
        return mapOf("detected" to (RootAccess.findExecutable() != null),
            "helper" to (helper.isFile && helper.canExecute()))
    }

    fun check(context: Context): Map<String, Any> {
        val discovered = detect(context)
        val executable = RootAccess.findExecutable()
            ?: return discovered + mapOf("granted" to false, "reason" to "not_found")
        val child = try {
            ProcessBuilder(executable, "-c", "id -u").redirectErrorStream(true).start()
        } catch (_: Exception) {
            return discovered + mapOf("granted" to false, "reason" to "unavailable")
        }
        return try {
            if (!awaitExit(child, 30_000)) {
                discovered + mapOf("granted" to false, "reason" to "timeout")
            } else {
                val granted = child.exitValue() == 0 && child.inputStream.bufferedReader().readText().trim() == "0"
                discovered + mapOf("granted" to granted, "reason" to if (granted) "granted" else "denied")
            }
        } finally { child.destroy() }
    }

    suspend fun start(context: Context) = withContext(Dispatchers.IO) {
        check(process == null) { "Root core is already running" }
        val helper = File(context.applicationInfo.nativeLibraryDir, "libhiddify-root.so")
        check(helper.isFile && helper.canExecute()) { "This APK has no executable root companion" }
        val executable = RootAccess.findExecutable() ?: error("Root is not available on this device")
        check(File(Settings.activeConfigPath).isFile) { "Select an available connection profile before starting root mode" }
        val directory = File(context.filesDir, "root-core").apply { mkdirs() }
        File(directory, "tmp").mkdirs()
        val options = JSONObject(com.hiddify.hiddify.nativecore.NativeCoreControl.effectiveOptions(context))
        val region = options.optString("region", "other")
        RegionalRouting.policy(context, region).forEach { (key, value) ->
            options.put(key, if (value is List<*>) org.json.JSONArray(value) else value)
        }
        options.put("enable-tun", true).put("privacy-root", true).put("wifi-vpn-sharing", Settings.wifiVpnSharing)
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
            .put("autoStart", Settings.startCoreAfterStartingService)
            .put("name", Settings.activeProfileName).put("debug", Settings.debugMode)
            .put("disableMemoryLimit", Settings.disableMemoryLimit).toString())
        envelope.setReadable(false, false); envelope.setReadable(true, true)
        envelope.setWritable(false, false); envelope.setWritable(true, true)
        val ready = CompletableDeferred<Unit>()
        val child = try {
            ProcessBuilder(executable, "-c", "exec ${quote(helper.absolutePath)} ${quote(envelope.absolutePath)}")
                .redirectErrorStream(true).start()
        } catch (error: Exception) {
            envelope.delete()
            throw error
        }
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
        val wasAlive = alive(child)
        runCatching { child.outputStream.close() }
        check(awaitExit(child, 15_000)) { "Root core shutdown timed out; routing cleanup is not confirmed" }
        process = null
        if (wasAlive) check(child.exitValue() == 0) { "Root core reported a shutdown failure; routing cleanup is not confirmed" }
    }

    fun isAlive(): Boolean = process?.let { alive(it) } == true
}
