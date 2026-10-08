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

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    override fun onCreate() {
        super.onCreate()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Thread({
                runCatching {
                    val manager = getSystemService(android.app.ActivityManager::class.java)
                    val entries = manager.getHistoricalProcessExitReasons(packageName, 0, 8)
                    val result = org.json.JSONArray()
                    entries.forEach { entry ->
                        // Record system evidence without config, IP addresses or crash trace secrets.
                        result.put(org.json.JSONObject().put("time", entry.timestamp).put("reason", entry.reason)
                            .put("status", entry.status).put("pssKiB", entry.pss).put("rssKiB", entry.rss))
                    }
                    val directory = java.io.File(Settings.workingDir).also { it.mkdirs() }
                    java.io.File(directory, "last-exits.json").writeText(result.toString())
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
        val notificationManager by lazy { application.getSystemService<NotificationManager>()!! }


    }

}
