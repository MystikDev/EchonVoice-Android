package com.echon.voice.core.update

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Periodic background update check that runs even when the app is closed — the
 * primary mechanism for keeping a directly-distributed (non-Play) install
 * current. On each run it:
 *  1. checks the manifest; if not newer, does nothing.
 *  2. if newer and a silent install is possible (Android 12+ with the install
 *     permission, same signing key) → downloads, verifies the SHA-256, and
 *     installs with no user interaction.
 *  3. otherwise → posts a notification the user taps to finish in-app.
 *
 * Reuses the same [UpdateChecker]/[ApkInstaller] as the in-app flow through a
 * Hilt entry point, so no custom WorkerFactory is required.
 */
class UpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun updateChecker(): UpdateChecker
        fun apkInstaller(): ApkInstaller
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)

        val status = runCatching { deps.updateChecker().check() }.getOrNull() ?: return Result.success()
        if (status !is UpdateStatus.Available) return Result.success()

        val installer = deps.apkInstaller()
        // Silent install works unattended only on Android 12+ with the install
        // permission; below that (or without it) the OS needs a foreground
        // confirmation, so fall back to a notification.
        val silentCapable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && installer.canInstall()

        return if (silentCapable) {
            runCatching {
                val apk = installer.download(UpdateConfig.LATEST_APK_URL, expectedSha256 = status.release.sha256)
                installer.install(apk)
            }.fold(
                onSuccess = {
                    UpdateNotifier.cancel(applicationContext)
                    Result.success()
                },
                // Transient network/IO failure (or a hash mismatch we shouldn't
                // install): retry with WorkManager's backoff rather than notify.
                onFailure = { Result.retry() },
            )
        } else {
            UpdateNotifier.notifyUpdateAvailable(applicationContext, status.release.versionName)
            Result.success()
        }
    }
}
