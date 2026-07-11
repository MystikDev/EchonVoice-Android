package com.echon.voice.model

import kotlinx.serialization.Serializable

/**
 * POST /v1/devices body — registers this install's FCM token so the backend can
 * push message/DM notifications to it. Sent (authenticated) whenever the user is
 * signed in and the token is issued or rotates.
 *
 * Property is `deviceToken` so the global snake_case JsonNamingStrategy emits
 * `device_token` (the server's required field). NOTE: an explicit
 * @SerialName("device_token") does NOT work here — the naming strategy overrides
 * it and the body goes out as `token`, which the server rejects with 422.
 *
 * `platform` has NO default: EchonJson uses encodeDefaults=false, so a default
 * value would be omitted from the body and the server would 422 on the missing
 * required `platform` field. Callers pass it explicitly.
 */
@Serializable
data class RegisterDeviceRequest(
    val deviceToken: String,
    val platform: String,
)
