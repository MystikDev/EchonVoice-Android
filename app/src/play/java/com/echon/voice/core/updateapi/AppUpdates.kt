package com.echon.voice.core.updateapi

import android.content.Context
import androidx.compose.runtime.Composable

/**
 * Google Play flavor: no self-updating. Google Play's Device & Network Abuse
 * policy prohibits an app updating itself outside of Play, so this flavor ships
 * without the updater, the install permissions, or the update UI — Play delivers
 * updates. These are the no-op counterparts to the direct-download seam.
 */
fun scheduleAppUpdates(context: Context) {
    // No-op: Google Play handles app updates.
}

@Composable
fun UpdateGate() {
    // No-op: no in-app update prompt on Play.
}
