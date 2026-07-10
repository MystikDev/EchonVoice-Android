package com.echon.voice.model

import kotlinx.serialization.Serializable

/**
 * POST /v1/devices body — registers this install's FCM token so the backend can
 * push message/DM notifications to it. Sent (authenticated) whenever the user is
 * signed in and the token is issued or rotates.
 */
@Serializable
data class RegisterDeviceRequest(
    val token: String,
    val platform: String = "android",
)
