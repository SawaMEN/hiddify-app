package com.hiddify.hiddify.nativeprofile

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hiddify.hiddify.MainActivity
import com.hiddify.hiddify.R

/** A separate, user-dismissible event, not the persistent VPN status notification. */
internal object NativeSubscriptionUpdateNotifier {
    private const val CHANNEL_ID = "subscription_updates"
    private const val NOTIFICATION_ID = 4103

    fun show(context: Context, changedNames: List<String>, restartingVpn: Boolean, reconnectPending: Boolean = false): Boolean {
        if (changedNames.isEmpty()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return false
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return false

        return runCatching {
            val platformManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                platformManager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.native_subscription_updates_channel),
                        NotificationManager.IMPORTANCE_DEFAULT),
                )
                if (platformManager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE)
                    return false
            }
            val title = if (changedNames.size == 1) {
                context.getString(R.string.native_profile_updated, changedNames.first())
            } else {
                context.getString(R.string.native_subscription_updates_count, changedNames.size)
            }
            val body = context.getString(when {
                restartingVpn -> R.string.native_subscription_updates_reconnecting
                reconnectPending -> R.string.native_subscription_updates_pending
                else -> R.string.native_subscription_updates_saved
            })
            val intent = PendingIntent.getActivity(context, 0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_logo)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(intent)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(NOTIFICATION_ID, notification)
            true
        }.onFailure { Log.w("A/Subscriptions", "Could not show subscription update notification", it) }
            .getOrDefault(false)
    }
}
