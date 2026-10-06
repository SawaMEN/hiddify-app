package com.hiddify.hiddify

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.hiddify.hiddify.bg.AppChangeReceiver
import com.hiddify.hiddify.privacy.VpnServiceVisibility
import go.Seq
import com.hiddify.hiddify.Application as BoxApplication

class Application : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    override fun onCreate() {
        super.onCreate()

        Seq.setContext(this)
        // Root mode does not use Android VpnService. Keep its manifest component out of
        // ordinary package-manager service queries while root mode is selected, and restore
        // it automatically after switching back to the normal Android VPN path.
        VpnServiceVisibility.sync(this, Settings.privacyUseRoot)

        registerReceiver(AppChangeReceiver(), IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addDataScheme("package")
        })
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
