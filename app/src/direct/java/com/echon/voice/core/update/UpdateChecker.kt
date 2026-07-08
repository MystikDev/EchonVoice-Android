package com.echon.voice.core.update

import com.echon.voice.BuildConfig
import com.echon.voice.model.AppRelease
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateStatus {
    data object UpToDate : UpdateStatus
    data class Available(val release: AppRelease, val mandatory: Boolean) : UpdateStatus
}

/**
 * Polls the hosted update manifest (GitHub) and compares it to the running build.
 * Direct distribution has no Play auto-update, so this is how installs learn about
 * new versions. Update-check failures never surface — a missing/unreachable
 * manifest must not block the app.
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val source: ReleaseManifestSource,
) {
    suspend fun check(): UpdateStatus = try {
        val release = source.fetch() ?: return UpdateStatus.UpToDate
        val installed = BuildConfig.VERSION_CODE
        if (release.versionCode > installed) {
            val mandatory = release.mandatory || installed < release.minSupportedVersionCode
            UpdateStatus.Available(release, mandatory)
        } else {
            UpdateStatus.UpToDate
        }
    } catch (e: Exception) {
        UpdateStatus.UpToDate
    }
}
