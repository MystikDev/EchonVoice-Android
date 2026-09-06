package com.echon.voice.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Update manifest hosted at distribution/latest.json (GitHub raw). The in-app
 * updater polls it because direct distribution has no Play auto-update.
 *
 * Field keys are pinned snake_case via @SerialName so the manifest decodes
 * deterministically regardless of the global naming strategy. The manifest must
 * use these exact keys — see distribution/README.md.
 */
@Serializable
data class AppRelease(
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    @SerialName("apk_url") val apkUrl: String,
    @SerialName("min_supported_version_code") val minSupportedVersionCode: Int = 0,
    @SerialName("mandatory") val mandatory: Boolean = false,
    @SerialName("notes") val notes: String? = null,
    /**
     * Hex SHA-256 of the release APK, required before an update can install.
     * Nullable for tolerant manifest decoding; missing/malformed digests fail
     * closed in the installer rather than bypassing integrity validation.
     */
    @SerialName("sha256") val sha256: String? = null,
)
