package com.echon.voice

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.echon.voice.core.designsystem.EchonTheme
import com.echon.voice.nav.AppRoot
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity Compose host. [AppRoot] routes on the auth phase
 * (loading → signedOut → needsEula → signedIn).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FLAG_SECURE keeps signed-in content (messages, DMs, the login email
        // field) out of the recents/overview snapshot and blocks screenshots of
        // private content. Trade-off: users can't screenshot in-app either.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        enableEdgeToEdge()
        setContent {
            EchonTheme {
                AppRoot()
            }
        }
    }
}
