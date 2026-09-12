package com.echon.voice.core.network

import com.echon.voice.model.RefreshResponse
import java.io.IOException
import kotlinx.serialization.SerializationException
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
    /** Null means the server rejected the credentials; transient failures preserve them. */
    fun refresh(refreshToken: String, generation: Long): RefreshResponse? {
        val request = Request.Builder()
            .url(ApiConfig.BASE_URL + "v1/auth/refresh")
            .post(ByteArray(0).toRequestBody())
            .header("X-Refresh-Token", refreshToken)
            .tag(SessionGeneration::class.java, SessionGeneration(generation))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) return null
                if (!response.isSuccessful) throw IOException("Session refresh is temporarily unavailable")
                val body = response.body?.string() ?: throw IOException("Missing session refresh response")
                EchonJson.decodeFromString(RefreshResponse.serializer(), body)
            }
        } catch (e: SerializationException) {
            throw IOException("Invalid session refresh response", e)
        }
        // Network and local storage IOExceptions propagate to apiCall. Neither is
        // evidence of revocation, and neither should erase a recoverable session.
    }
}
