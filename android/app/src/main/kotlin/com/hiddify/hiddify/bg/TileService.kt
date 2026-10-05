package com.hiddify.hiddify.bg

import android.app.PendingIntent
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.hiddify.hiddify.MainActivity
import com.hiddify.hiddify.Settings
import com.hiddify.hiddify.constant.ServiceMode
import com.hiddify.hiddify.constant.Status

class TileService : TileService(), ServiceConnection.Callback {

    companion object {
        private const val TAG = "A/TileService"
    }

    private val connection = ServiceConnection(this, this)

    override fun onServiceStatusChanged(status: Status) {
        qsTile?.apply {
            state = when (status) {
                Status.Started -> Tile.STATE_ACTIVE
                Status.Stopped -> Tile.STATE_INACTIVE
                else -> Tile.STATE_UNAVAILABLE
            }
            updateTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        connection.connect()
    }

    override fun onStopListening() {
        connection.disconnect()
        super.onStopListening()
    }

    private fun toggleService() {
        when (connection.status) {
            Status.Stopped -> startFromTile()
            Status.Started -> BoxService.stop()
            else -> Log.d(TAG, "ignoring tile click while service is transitioning")
        }
    }

    private fun startFromTile() {
        if (Settings.activeConfigPath.isBlank()) {
            Log.w(TAG, "cannot start from tile without an active config")
            openMainApp()
            return
        }
        // Root mode uses ProxyService + the root companion and must not request Android
        // VpnService permission. BoxService.start() synchronizes the component visibility.
        if (Settings.serviceMode == ServiceMode.VPN && !Settings.privacyUseRoot && VpnService.prepare(this) != null) {
            Log.w(TAG, "VPN permission is required; opening the app")
            openMainApp()
            return
        }

        Settings.startCoreAfterStartingService = true
        try {
            BoxService.start()
        } catch (error: Exception) {
            Log.e(TAG, "Tile could not start foreground service", error)
            onServiceStatusChanged(Status.Stopped)
            openMainApp()
            return
        }
        qsTile?.apply {
            state = Tile.STATE_UNAVAILABLE
            updateTile()
        }
    }

    private fun openMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onClick() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isKeyguardLocked) {
            unlockAndRun { toggleService() }
        } else {
            toggleService()
        }
    }
}
