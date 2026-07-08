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
import java.security.MessageDigest
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
 *
 * Two integrity controls apply: (1) Android's OS verifier rejects any APK not
 * signed with the installed app's key; (2) when the manifest carries a [sha256],
 * [download] verifies the bytes against it and refuses a mismatch — so even a
 * same-key but unexpected/rolled-back artifact from a compromised download
 * source is caught before install.
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

    /**
     * Streams the APK to cache over the plain client. When [expectedSha256] is
     * non-null, the downloaded file's SHA-256 must match it (case-insensitive
     * hex) or the file is deleted and an [IOException] is thrown before any
     * install is attempted.
     */
    suspend fun download(apkUrl: String, expectedSha256: String? = null): File = withContext(Dispatchers.IO) {
        val response = client.newCall(Request.Builder().url(apkUrl).build()).execute()
        response.use {
            if (!it.isSuccessful) throw IOException("Download failed (${it.code}).")
            val body = it.body ?: throw IOException("Empty download body.")
            val file = File(context.cacheDir, "echon-update.apk")
            body.byteStream().use { input -> file.outputStream().use { out -> input.copyTo(out) } }

            if (expectedSha256 != null) {
                val actual = file.sha256Hex()
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    file.delete()
                    throw IOException("APK integrity check failed: expected $expectedSha256, got $actual.")
                }
            }
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

/** Lowercase hex SHA-256 of this file's contents, streamed in fixed-size chunks. */
internal fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHexString()
}

/** Lowercase hex SHA-256 of these bytes (extracted for unit testing the digest). */
internal fun ByteArray.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256").digest(this).toHexString()

private fun ByteArray.toHexString(): String {
    val hex = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xff
        hex.append("0123456789abcdef"[v ushr 4])
        hex.append("0123456789abcdef"[v and 0x0f])
    }
    return hex.toString()
}
