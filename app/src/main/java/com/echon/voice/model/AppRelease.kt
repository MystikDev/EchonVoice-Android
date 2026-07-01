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
     * Lowercase hex SHA-256 of the release APK. When present, the installer
     * verifies the downloaded bytes against it before install (defense-in-depth
     * on top of Android's same-signing-key check). Nullable so older manifests
     * still decode; absence falls back to the OS verifier alone.
     */
    @SerialName("sha256") val sha256: String? = null,
)
