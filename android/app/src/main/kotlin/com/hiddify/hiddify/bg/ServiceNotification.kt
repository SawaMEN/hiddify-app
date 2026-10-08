package com.hiddify.hiddify.bg

import android.Manifest
import android.content.pm.PackageManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.hiddify.hiddify.Application
import com.hiddify.hiddify.MainActivity
import com.hiddify.hiddify.R
import com.hiddify.hiddify.Settings
import com.hiddify.hiddify.constant.Action
import com.hiddify.core.libbox.Libbox
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive

import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
class ServiceNotification(private val service: Service) : BroadcastReceiver() {
    companion object {
        private const val notificationChannel = "service"
        private var foregroundOwner: ServiceNotification? = null
        const val flags = PendingIntent.FLAG_IMMUTABLE

        fun hasRuntimePermission(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(Application.application, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED

        fun checkPermission(): Boolean {
            if (!hasRuntimePermission() || !NotificationManagerCompat.from(Application.application).areNotificationsEnabled()) return false
            return Application.notification.getNotificationChannel(notificationChannel)?.importance != NotificationManager.IMPORTANCE_NONE
        }

        // Called on Android main after granting permission or returning from system settings.
        fun refreshActive() {
            val owner = foregroundOwner ?: return
            if (owner.closed) return
            runCatching {
                owner.service.startForeground(owner.notificationId, owner.notificationBuilder.build())
                owner.updatePolling()
            }.onFailure { Log.w("notification", "failed to refresh foreground notification", it) }
        }

        fun settingsIntent(): Intent = if (
            NotificationManagerCompat.from(Application.application).areNotificationsEnabled() &&
            Application.notification.getNotificationChannel(notificationChannel) != null) {
            Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, Application.application.packageName)
                .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, notificationChannel)
        } else {
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, Application.application.packageName)
        }
    }

    // VPN and proxy have independent Android foreground IDs. Destroying one service must not
    // remove the replacement service's notification.
    private val notificationId = if (service is VPNService) 1 else 2
    private val streamingCoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var closed = true
    private var pollingGeneration = 0L
    private var receiverRegistered = false
    private var profileName = "VetrOFF Client"

    private val notificationBuilder by lazy {
        NotificationCompat.Builder(service, notificationChannel)
                .setShowWhen(false)
                .setOngoing(true)
                .setContentTitle("VetrOFF Client")
                .setOnlyAlertOnce(true)
                .setSmallIcon(R.drawable.ic_stat_logo)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(
                        PendingIntent.getActivity(
                                service,
                                0,
                                Intent(
                                        service,
                                        MainActivity::class.java
                                ).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                                flags
                        )
                )
                .setPriority(NotificationCompat.PRIORITY_LOW).apply {
                    addAction(
                            NotificationCompat.Action.Builder(
                                    0, service.getText(R.string.stop), PendingIntent.getBroadcast(
                                    service,
                                    0,
                                    Intent(Action.SERVICE_CLOSE).setPackage(
                                        Application.application.packageName
                                    ),
                                    flags
                            )
                            ).build()
                    )
                }
    }

    fun show(profileName: String, @StringRes contentTextId: Int) {
        this.profileName = profileName.takeIf { it.isNotBlank() } ?: "VetrOFF Client"
        Application.notification.createNotificationChannel(
            NotificationChannel(notificationChannel, "VetrOFF Client", NotificationManager.IMPORTANCE_LOW)
        )
        service.startForeground(
            notificationId, notificationBuilder
                .setContentTitle(profileName.takeIf { it.isNotBlank() } ?: "VetrOFF Client")
                .setContentText(service.getString(contentTextId)).build()
        )
        foregroundOwner?.takeIf { it !== this }?.stopListenSystemInfo()
        closed = false
        foregroundOwner = this
    }

    suspend fun start() = withContext(Dispatchers.Main.immediate) {
        if (closed || foregroundOwner !== this@ServiceNotification) return@withContext
        registerReceiver()
        updatePolling()
    }

    private fun updatePolling() {
        if (!closed && foregroundOwner === this && Settings.dynamicNotification && checkPermission() &&
            Application.powerManager.isInteractive) {
            if (streamingJob?.isActive != true) startListenSystemInfo()
        } else {
            stopListenSystemInfo()
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            service,
            this,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun updateStatus(status: com.hiddify.hiddify.nativecore.NativeSystemStats) {
        if (closed || foregroundOwner !== this || !checkPermission()) return
        fun rate(bytes: Long) = if (status.speedAvailable) "${Libbox.formatBytes(bytes)}/s" else "—"
        val content = "${rate(status.uplink)} ↑  ${rate(status.downlink)} ↓\n${status.currentOutbound}"
        val title = status.currentProfile.takeIf { it.isNotBlank() } ?: profileName
        Application.notificationManager.notify(notificationId,
            notificationBuilder.setContentTitle(title).setContentText(content).build())
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                refreshActive()
            }

            Intent.ACTION_SCREEN_OFF -> {
                stopListenSystemInfo()
            }
        }
    }

    fun close() {
        closed = true
        stopListenSystemInfo()
        val ownsNotification = foregroundOwner === this
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (ownsNotification) foregroundOwner = null
        if (receiverRegistered) {
            runCatching { service.unregisterReceiver(this) }
                .onFailure { Log.w("notification", "receiver was already unregistered", it) }
            receiverRegistered = false
        }
    }

    private var streamingJob: Job? = null

    fun startListenSystemInfo() {
        if (closed || foregroundOwner !== this || !Settings.dynamicNotification || !checkPermission() ||
            !Application.powerManager.isInteractive || streamingJob?.isActive == true) return
        val generation = ++pollingGeneration
        streamingJob = streamingCoroutineScope.launch(Dispatchers.Main.immediate) {
            com.hiddify.hiddify.nativecore.NativeStatsFeed.snapshots.collect { snapshot ->
                if (!closed && generation == pollingGeneration) updateStatus(snapshot)
            }
        }
    }
    fun stopListenSystemInfo() {
        pollingGeneration++
        streamingJob?.cancel()
        streamingJob = null
    }

    fun destroy() {
        close()
        streamingCoroutineScope.cancel()
    }
}
