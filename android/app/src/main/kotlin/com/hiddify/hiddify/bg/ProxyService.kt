package com.hiddify.hiddify.bg

import android.app.Service
import android.content.Intent
import com.hiddify.core.libbox.Notification
import com.hiddify.hiddify.Settings

class ProxyService :
    Service(),
    PlatformInterfaceWrapper {
    private val service = BoxService(this, this)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            if (!Settings.connectionDesired) { stopSelf(); return START_NOT_STICKY }
            Settings.startCoreAfterStartingService = true
        }
        return service.onStartCommand()
    }

    override fun onBind(intent: Intent) = service.onBind(intent)

    override fun onDestroy() {
        service.onDestroy()
        super.onDestroy()
    }

    override fun sendNotification(notification: Notification) = Unit
}
