package com.echon.voice.model

import kotlinx.serialization.Serializable

/** PATCH /v1/me/profile — only non-null fields are updated. Returns the user. */
@Serializable
data class ProfileUpdateRequest(
    val about: String? = null,
    val allowDmsFrom: String? = null,
    val avatar: String? = null,
)

/** POST /v1/me/password. */
@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)
