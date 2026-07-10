package com.echon.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.echon.voice.core.designsystem.EchonTheme
import com.echon.voice.core.push.ChatDeepLink
import com.echon.voice.core.push.DeepLinkStore
import com.echon.voice.core.push.MessageNotifier
import com.echon.voice.nav.AppRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity Compose host. [AppRoot] routes on the auth phase
 * (loading → signedOut → needsEula → signedIn).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    @Inject lateinit var deepLinkStore: DeepLinkStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FLAG_SECURE keeps signed-in content (messages, DMs, the login email
        // field) out of the recents/overview snapshot and blocks screenshots of
        // private content. Trade-off: users can't screenshot in-app either.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        maybeRequestNotificationPermission()
        handleNotificationIntent(intent)
        enableEdgeToEdge()
        setContent {
            EchonTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    /** A tapped message notification carries the target conversation; route to it. */
    private fun handleNotificationIntent(intent: Intent?) {
        val channelId = intent?.getStringExtra(MessageNotifier.EXTRA_CHANNEL_ID) ?: return
        deepLinkStore.submit(
            ChatDeepLink(
                channelId = channelId,
                channelName = intent.getStringExtra(MessageNotifier.EXTRA_CHANNEL_NAME) ?: "channel",
                channelKind = intent.getStringExtra(MessageNotifier.EXTRA_CHANNEL_KIND) ?: "server",
            ),
        )
    }

    /**
     * Ask once for POST_NOTIFICATIONS (Android 13+) so the background updater's
     * "update ready" fallback notification can be shown. Older versions grant it
     * at install time; if the user declines, silent self-update still works.
     */
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
