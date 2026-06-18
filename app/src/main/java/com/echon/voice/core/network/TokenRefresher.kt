package com.echon.voice.core.network

import com.echon.voice.model.RefreshResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Performs the synchronous `POST /v1/auth/refresh` token exchange used by
 * [TokenAuthenticator]. Uses a dedicated pinned client with **no** authenticator
 * so refreshing can never recurse into itself. Refresh tokens rotate on every
 * call, so the rotated value (when returned) must be persisted.
 */
@Singleton
class TokenRefresher @Inject constructor(
    @Named("refresh") private val client: OkHttpClient,
) {
    /** @return the new tokens, or null if the refresh failed. */
    fun refresh(refreshToken: String): RefreshResponse? {
        val request = Request.Builder()
            .url(ApiConfig.BASE_URL + "v1/auth/refresh")
            .post(ByteArray(0).toRequestBody())
            .header("X-Refresh-Token", refreshToken)
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                EchonJson.decodeFromString(RefreshResponse.serializer(), body)
            }
        }.getOrNull()
    }
}
