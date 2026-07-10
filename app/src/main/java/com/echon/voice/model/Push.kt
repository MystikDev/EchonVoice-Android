package com.echon.voice.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * POST /v1/devices body — registers this install's FCM token so the backend can
 * push message/DM notifications to it. Sent (authenticated) whenever the user is
 * signed in and the token is issued or rotates.
 *
 * Field is `device_token` to match the server contract (explicit @SerialName
 * overrides the global snake_case strategy, which would otherwise emit `token`).
 */
@Serializable
data class RegisterDeviceRequest(
    @SerialName("device_token") val token: String,
    val platform: String = "android",
)
