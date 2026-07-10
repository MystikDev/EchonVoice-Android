package com.echon.voice.core.push

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
 * Posts a "new message" notification from an FCM push. Tapping it opens the app
 * and deep-links to the originating channel/DM via [MainActivity] extras.
 *
 * The block filter still applies in-app; the backend is responsible for not
 * pushing to a user who has blocked the sender.
 */
object MessageNotifier {
    private const val CHANNEL_ID = "echon_messages"

    /** Intent extras [MainActivity] reads to deep-link a notification tap. */
    const val EXTRA_CHANNEL_ID = "echon.notif.channelId"
    const val EXTRA_CHANNEL_NAME = "echon.notif.channelName"
    const val EXTRA_CHANNEL_KIND = "echon.notif.channelKind"

    fun notify(
        context: Context,
        channelId: String?,
        channelName: String?,
        channelKind: String?,
        title: String,
        body: String,
    ) {
        ensureChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            channelId?.let {
                putExtra(EXTRA_CHANNEL_ID, it)
                putExtra(EXTRA_CHANNEL_NAME, channelName)
                putExtra(EXTRA_CHANNEL_KIND, channelKind)
            }
        }
        // Distinct request code per conversation so taps route to the right one and
        // a conversation's notification updates in place instead of stacking.
        val requestCode = channelId?.hashCode() ?: 0
        val pending = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(requestCode, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Messages",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply { description = "New messages and direct messages." },
                )
            }
        }
    }
}
