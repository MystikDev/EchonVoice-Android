package com.echon.voice.core.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Downloads an update APK over the plain client and installs it via
 * [PackageInstaller]. On Android 12+ a self-update signed with the same key
 * installs **silently** (no confirmation) thanks to
 * `setRequireUserAction(USER_ACTION_NOT_REQUIRED)` + the
 * UPDATE_PACKAGES_WITHOUT_USER_ACTION permission. On older devices, or when the
 * system declines, [InstallResultReceiver] forwards the user to the installer UI.
 * Integrity is guaranteed by the OS verifier (same signing key as the installed app).
 */
@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("plain") private val client: OkHttpClient,
) {
    /** Whether the app may install packages at all (API 26+ runtime grant). */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** Sends the user to grant "install unknown apps" for this app (one-time). */
    fun requestInstallPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Streams the APK to cache over the plain client. */
    suspend fun download(apkUrl: String): File = withContext(Dispatchers.IO) {
        val response = client.newCall(Request.Builder().url(apkUrl).build()).execute()
        response.use {
            if (!it.isSuccessful) throw IOException("Download failed (${it.code}).")
            val body = it.body ?: throw IOException("Empty download body.")
            val file = File(context.cacheDir, "echon-update.apk")
            body.byteStream().use { input -> file.outputStream().use { out -> input.copyTo(out) } }
            file
        }
    }

    /**
     * Installs the APK. On Android 12+ this is silent for a same-key self-update;
     * otherwise the system installer is surfaced via [InstallResultReceiver].
     */
    fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("echon", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }
}
