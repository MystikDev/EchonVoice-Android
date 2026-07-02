package com.echon.voice.core.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.echon.voice.MainActivity
import com.echon.voice.R

/**
 * Posts the "update ready" system notification — the fallback the background
 * [UpdateWorker] uses when it can't silently self-install (install permission
 * not granted, or Android < 12). Tapping it opens the app, whose on-launch
 * updater finishes the install (granting the one-time permission if needed).
 *
 * On Android 12+ with the install permission, updates install silently and this
 * notification is never shown.
 */
object UpdateNotifier {
    private const val CHANNEL_ID = "echon_updates"
    private const val NOTIFICATION_ID = 4310

    fun notifyUpdateAvailable(context: Context, versionName: String?) {
        ensureChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = "A new version of Echon" + (versionName?.let { " ($it)" } ?: "") +
            " is ready. Tap to install."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Update available")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) =
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "App updates",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = "Notifies you when a new version of Echon is ready to install."
                    },
                )
            }
        }
    }
}
