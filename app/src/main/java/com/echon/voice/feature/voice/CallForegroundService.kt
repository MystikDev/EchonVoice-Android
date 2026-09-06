package com.echon.voice.feature.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.echon.voice.MainActivity
import com.echon.voice.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/** Owns the foreground lifetime of call audio, including screen-share playback. */
@AndroidEntryPoint
class CallForegroundService : Service() {
    @Inject lateinit var calls: VoiceCallStore
    private var ownedGeneration = -1L
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val generation = intent?.getLongExtra(EXTRA_GENERATION, -1) ?: -1
        if (generation != calls.sessionGeneration) return START_NOT_STICKY
        if (intent?.action == ACTION_LEAVE) {
            calls.leave()
            return START_NOT_STICKY
        }
        ownedGeneration = generation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Voice calls", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val leave = PendingIntent.getService(this, 1,
            Intent(this, CallForegroundService::class.java).setAction(ACTION_LEAVE)
                .putExtra(EXTRA_GENERATION, generation),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Echon")
            .setContentText("In a voice call")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(open)
            .addAction(0, "Leave call", leave)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            started.value = generation
        } catch (_: SecurityException) {
            // Runtime permission can be revoked between the UI check and service startup.
            calls.stopIfSession(generation)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (started.value == ownedGeneration) started.value = null
        calls.stopIfSession(ownedGeneration)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "echon_call"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_LEAVE = "com.echon.voice.LEAVE_CALL"
        private const val EXTRA_GENERATION = "call_generation"
        private val started = MutableStateFlow<Long?>(null)

        suspend fun start(context: Context, generation: Long) {
            val intent = Intent(context, CallForegroundService::class.java).putExtra(EXTRA_GENERATION, generation)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
            withTimeout(5_000) { started.first { it == generation } }
        }

        fun stop(context: Context) {
            started.value = null
            context.stopService(Intent(context, CallForegroundService::class.java))
        }
    }
}
