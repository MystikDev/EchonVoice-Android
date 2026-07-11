package com.echon.voice.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/** POST /v1/auth/login body. `remember_me` extends the refresh token to 60 days. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    // @EncodeDefault forces this to serialize even though EchonJson has
    // encodeDefaults=false; otherwise the default is dropped and the user gets a
    // 14-day (not 60-day) refresh token.
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val rememberMe: Boolean = true,
)

/** POST /v1/auth/register body. `date_of_birth` is required (YYYY-MM-DD). */
@Serializable
data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
    val dateOfBirth: String,
)

/** POST /v1/auth/login and register → {token, refresh_token, user}. */
@Serializable
data class LoginResponse(
    val token: String,
    val refreshToken: String? = null,
    val user: User? = null,
)

/** POST /v1/auth/refresh → {token, refresh_token?}. Refresh tokens rotate every call. */
@Serializable
data class RefreshResponse(
    val token: String,
    val refreshToken: String? = null,
)

/** GET /v1/me → {user: {...}}. */
@Serializable
data class MeResponse(val user: User)
