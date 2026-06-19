package com.echon.voice.model

import kotlinx.serialization.Serializable

/**
 * Update manifest hosted at echon-voice.com/app/latest.json. The in-app updater
 * polls it because direct (website) distribution has no Play auto-update.
 * See distribution/README.md for the hosting contract.
 */
@Serializable
data class AppRelease(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val minSupportedVersionCode: Int = 0,
    val mandatory: Boolean = false,
    val notes: String? = null,
)
