package com.echon.voice.core.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/**
 * Receives [PackageInstaller] session results. On Android 12+ a self-update with
 * the same signing key installs silently (no callback action needed). When the
 * system still requires confirmation (older Android, or conditions unmet), it
 * sends [PackageInstaller.STATUS_PENDING_USER_ACTION] with an intent to launch
 * the installer UI — we forward the user there as a graceful fallback.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            }
            else -> Unit // success / failure — nothing to do (the OS applies a successful self-update)
        }
    }
}
