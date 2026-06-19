package com.echon.voice.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
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
 * Downloads an update APK over the pinned client and hands it to the system
 * package installer. The OS verifies the new APK is signed with the same key as
 * the installed app, so a stable release keystore is essential (see
 * distribution/README.md).
 */
@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("plain") private val client: OkHttpClient,
) {
    /** Whether the app is currently allowed to request package installs (API 26+). */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** Sends the user to grant "install unknown apps" for this app. */
    fun requestInstallPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Streams the APK to cache over the pinned client. */
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

    /** Launches the system installer for a downloaded APK via a content URI. */
    fun launchInstall(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
