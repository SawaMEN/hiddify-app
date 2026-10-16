package com.hiddify.hiddify

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.hiddify.hiddify.privacy.VpnServiceVisibility
import com.hiddify.hiddify.Application as BoxApplication

class Application : Application() {

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            com.hiddify.hiddify.nativeui.clearNativeInstalledAppIcons()
        }
    }

    @Suppress("DEPRECATION")
    override fun onLowMemory() {
        super.onLowMemory()
        com.hiddify.hiddify.nativeui.clearNativeInstalledAppIcons()
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    override fun onCreate() {
        super.onCreate()
        Thread.getDefaultUncaughtExceptionHandler()?.let { previous ->
            Thread.setDefaultUncaughtExceptionHandler(
                com.hiddify.hiddify.nativelog.NativeCrashRecorder(java.io.File(filesDir, "app.log"), previous),
            )
        }

        com.hiddify.hiddify.nativeupdate.NativeAppUpdates.initialize(this)
        com.hiddify.hiddify.nativerouting.NativeInstalledApps.observePackageChanges(this)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Thread({
                runCatching {
                    val manager = getSystemService(android.app.ActivityManager::class.java)
                    val entries = manager.getHistoricalProcessExitReasons(packageName, 0, 8)
                    val result = org.json.JSONArray()
                    val lines = mutableListOf<String>()
                    entries.forEach { entry ->
                        val phase = com.hiddify.hiddify.nativelog.NativeVpnProcessPhase.fromSummary(entry.processStateSummary)?.token
                        // Keep the system's exit reason and description; no configuration or native trace dump.
                        result.put(org.json.JSONObject().put("time", entry.timestamp).put("reason", entry.reason)
                            .put("reasonName", com.hiddify.hiddify.nativelog.NativeProcessExitReason.name(entry.reason))
                            .put("vpnPhase", phase).put("pid", entry.pid).put("process", entry.processName)
                            .put("importance", entry.importance).put("description", entry.description?.take(1024))
                            .put("status", entry.status).put("pssKiB", entry.pss).put("rssKiB", entry.rss))
                        lines += com.hiddify.hiddify.nativelog.NativeProcessExitReason.format(entry.timestamp,
                            entry.reason, entry.status, entry.pid, entry.processName, entry.importance, phase, entry.description)
                    }
                    // Core paths may still be "./" before the first VPN start.
                    java.io.File(filesDir, "last-exits.json").writeText(result.toString())
                    java.io.File(filesDir, "last-exits.log").writeText(lines.asReversed().joinToString("\n", postfix = "\n"))
                }.onFailure { android.util.Log.w("A/Diagnostics", "Unable to collect previous process exits", it) }
            }, "process-exit-diagnostics").start()
        }
        // Root mode does not use Android VpnService. Keep its manifest component out of
        // ordinary package-manager service queries while root mode is selected, and restore
        // it automatically after switching back to the normal Android VPN path.
        VpnServiceVisibility.sync(this, Settings.privacyUseRoot)
    }

    companion object {
        lateinit var application: BoxApplication
        val notification by lazy { application.getSystemService<NotificationManager>()!! }
        val connectivity by lazy { application.getSystemService<ConnectivityManager>()!! }
        val packageManager by lazy { application.packageManager }
        val powerManager by lazy { application.getSystemService<PowerManager>()!! }


    }

}
