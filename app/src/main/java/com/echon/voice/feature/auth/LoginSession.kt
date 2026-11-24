package com.echon.voice.feature.auth

import com.echon.voice.core.network.ApiException
import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.SessionStore
import com.echon.voice.core.network.apiCall
import com.echon.voice.model.EmailLoginChallenge
import com.echon.voice.model.LoginRequest
import com.echon.voice.model.LoginResponse
import com.echon.voice.model.LoginVerificationRequest
import javax.inject.Inject

/** A challenge never becomes an authenticated session. Only a valid token does. */
class LoginSession @Inject constructor(
    private val api: EchonApi,
    private val session: SessionStore,
) {
    suspend fun logIn(email: String, password: String): EmailLoginChallenge? {
        val response = apiCall { api.login(LoginRequest(email, password)) }
        if (response.twoFactorRequired) {
            val id = response.challengeId
            if (response.method != "email" || id.isNullOrBlank()) {
                throw ApiException.Decoding(IllegalStateException("Unsupported or incomplete login challenge"))
            }
            return EmailLoginChallenge(id)
        }
        val token = response.token
        if (token.isNullOrBlank()) {
            throw ApiException.Decoding(IllegalStateException("Login response has no token"))
        }
        store(LoginResponse(token, response.refreshToken, response.user))
        return null
    }

    suspend fun verify(challengeId: String, code: String) {
        val response = apiCall { api.verifyLogin(LoginVerificationRequest(challengeId, code)) }
        store(response)
    }

    private suspend fun store(response: LoginResponse) {
        if (response.token.isBlank()) {
            throw ApiException.Decoding(IllegalStateException("Login response has no token"))
        }
        // Preserve a refresh token captured from the verification response cookie.
        apiCall { session.setTokens(response.token, response.refreshToken) }
    }
}
