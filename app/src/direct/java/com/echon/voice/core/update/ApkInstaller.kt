package com.echon.voice.core.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import com.echon.voice.BuildConfig
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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
 * Downloads require a SHA-256 digest, a fixed HTTPS source, and bounded size.
 * Before install the package and manifest version must match this self-update.
 * Android's installer verifies the existing package's signing key/rotation lineage.
 * A hash fetched alongside an artifact is integrity metadata, not an independent signature.
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Downloads to a unique private cache file; a missing/mismatched hash fails closed. */
    suspend fun download(apkUrl: String, expectedSha256: String? = null): File = withContext(Dispatchers.IO) {
        requireValidUpdateHash(expectedSha256)
        require(apkUrl == UpdateConfig.LATEST_APK_URL) { "Unexpected update source" }
        val coroutineContext = currentCoroutineContext()
        val file = File.createTempFile("echon-update-", ".apk", context.cacheDir)
        try {
            client.newCall(Request.Builder().url(apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed (${response.code}).")
                val body = response.body ?: throw IOException("Empty download body.")
                if (body.contentLength() > MAX_APK_BYTES) throw IOException("Update is too large.")
                body.byteStream().use { input -> file.outputStream().use { out ->
                    copyUpdateCapped(input, out, MAX_APK_BYTES) { coroutineContext.ensureActive() }
                } }
            }
            coroutineContext.ensureActive()
            if (!file.sha256Hex().equals(expectedSha256, ignoreCase = true)) {
                throw IOException("APK integrity check failed.")
            }
            file
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    /**
     * Installs the APK. On Android 12+ this is silent for a same-key self-update;
     * otherwise the system installer is surfaced via [InstallResultReceiver].
     */
    fun install(apk: File, expectedVersionCode: Int) {
        try {
            val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
                ?: throw IOException("Invalid APK.")
            requireSelfUpdate(info.packageName, context.packageName,
                PackageInfoCompat.getLongVersionCode(info), BuildConfig.VERSION_CODE, expectedVersionCode)
            installVerified(apk)
        } finally {
            apk.delete()
        }
    }

    private fun installVerified(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        try {
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
        } catch (e: Exception) {
            installer.abandonSession(sessionId)
            throw e
        }
    }
}

internal const val MAX_APK_BYTES = 256L * 1024 * 1024

internal fun requireValidUpdateHash(hash: String?) {
    require(hash != null && Regex("[0-9a-fA-F]{64}").matches(hash)) { "Update requires a SHA-256 digest." }
}

internal fun requireSelfUpdate(candidate: String, installed: String, version: Long, current: Int, expected: Int) {
    require(candidate == installed) { "Update is for a different application." }
    require(version > current && version == expected.toLong()) { "Unexpected update version." }
    // Android verifies the installed package's signing key/rotation lineage at commit.
}

internal fun copyUpdateCapped(input: InputStream, output: OutputStream, limit: Long, checkActive: () -> Unit = {}) {
    val buffer = ByteArray(8192)
    var total = 0L
    while (true) {
        checkActive()
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > limit) throw IOException("Update is too large.")
        output.write(buffer, 0, count)
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
