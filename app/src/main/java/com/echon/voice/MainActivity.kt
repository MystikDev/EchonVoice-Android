package com.echon.voice

import android.os.Bundle
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
        enableEdgeToEdge()
        setContent {
            EchonTheme {
                AppRoot()
            }
        }
    }
}
