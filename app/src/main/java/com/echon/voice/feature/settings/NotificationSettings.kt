package com.echon.voice.feature.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.echon.voice.R
import com.echon.voice.core.push.MessageNotifier
import com.echon.voice.core.push.PushRegistrationStatus

@Composable
internal fun NotificationSettings(status: PushRegistrationStatus, onRetry: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(MessageNotifier.canPostNotifications(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = MessageNotifier.canPostNotifications(context)
        if (allowed) onRetry()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        MessageNotifier.ensureChannel(context)
        allowed = MessageNotifier.canPostNotifications(context)
    }
    fun openSettings() {
        val intent = if (Build.VERSION.SDK_INT >= 26) {
            val appBlocked = !NotificationManagerCompat.from(context).areNotificationsEnabled()
            Intent(if (appBlocked) Settings.ACTION_APP_NOTIFICATION_SETTINGS else Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                if (!appBlocked) putExtra(Settings.EXTRA_CHANNEL_ID, MessageNotifier.CHANNEL_ID)
            }
        } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        context.startActivity(intent)
    }
    Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_notification), contentDescription = null)
            Text("Notifications", style = MaterialTheme.typography.titleMedium)
        }
        Text(if (allowed) "Allowed in Android settings" else "Blocked in Android settings", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!allowed) {
            Text("Allow Echon notifications and the Messages category in Android settings.")
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else openSettings()
            }) { Text("Enable notifications") }
        }
        Text(when (status) {
            PushRegistrationStatus.REGISTERED -> "This device is registered for message notifications."
            PushRegistrationStatus.REGISTERING -> "Connecting message notifications…"
            PushRegistrationStatus.FAILED -> "Couldn't connect message notifications. Check your connection and retry."
            PushRegistrationStatus.UNAVAILABLE -> "Push notifications are unavailable in this build. Install the latest official Echon app."
            PushRegistrationStatus.SIGNED_OUT -> "Sign in to receive message notifications."
        }, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = ::openSettings) { Text("Open Android notification settings") }
        TextButton(onClick = onRetry, enabled = status != PushRegistrationStatus.REGISTERING) { Text("Retry push registration") }
        TextButton(onClick = { MessageNotifier.notify(context, null, null, null, "Echon notifications", "Your Android notification settings allow local alerts.") }, enabled = allowed) { Text("Send test notification") }
        Text("The test checks this phone's settings. Delivery of messages also requires a network connection and the push service.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
