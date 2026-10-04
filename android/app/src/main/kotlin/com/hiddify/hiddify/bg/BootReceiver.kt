package com.hiddify.hiddify.bg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.hiddify.hiddify.Settings
import com.hiddify.hiddify.constant.ServiceMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "A/BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (!Settings.startedByUser) return@launch
                if (Settings.activeConfigPath.isBlank()) {
                    Log.w(TAG, "skipping automatic restart without an active config")
                    return@launch
                }
                if (Settings.serviceMode == ServiceMode.VPN && VpnService.prepare(context) != null) {
                    Log.w(TAG, "skipping automatic restart because VPN permission is missing")
                    return@launch
                }
                if (Settings.dynamicNotification && !ServiceNotification.checkPermission()) {
                    Log.w(TAG, "skipping automatic restart because notification permission is missing")
                    return@launch
                }

                Settings.startCoreAfterStartingService = true
                runCatching { BoxService.start() }
                    .onFailure { Log.e(TAG, "failed to restart service after boot/package update", it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
