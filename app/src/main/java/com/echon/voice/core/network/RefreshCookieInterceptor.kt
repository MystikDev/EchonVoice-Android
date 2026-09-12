package com.echon.voice.core.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Captures the backend's rotating `refresh_token`, which is delivered ONLY as an
 * HttpOnly `Set-Cookie` on login and refresh (never in a response body). The app
 * has no CookieJar, so without this the refresh token is never seen and the
 * session dies when the 15-minute access token expires.
 *
 * HttpOnly hides the cookie from JavaScript, not from the HTTP layer, so this
 * response interceptor can read it and hand it to [SessionStore] (which persists
 * it). Attached to the clients that hit the auth endpoints.
 */
@Singleton
class RefreshCookieInterceptor @Inject constructor(
    private val session: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val generation = request.tag(SessionGeneration::class.java)?.value ?: session.generation
        val response = chain.proceed(request)
        // Only trust a refresh_token Set-Cookie from a SUCCESSFUL response to an
        // auth endpoint on the Echon API host. Otherwise a third-party host (e.g.
        // an image URL that returns Set-Cookie) could forge/replace the session's
        // refresh token.
        try {
            if (response.isSuccessful &&
                TlsPinning.isApiOrigin(response.request.url) &&
                response.request.url.encodedPath in TOKEN_ENDPOINTS
            ) {
                response.headers("Set-Cookie").forEach { header ->
                    parseRefreshToken(header)?.let { session.onRefreshCookie(it, generation) }
                }
            }
            return response
        } catch (e: Exception) {
            response.close()
            throw e
        }
    }

    internal companion object {
        private const val COOKIE_NAME = "refresh_token"
        private val TOKEN_ENDPOINTS = setOf("/v1/auth/login", "/v1/auth/register", "/v1/auth/refresh")

        /** Pull `<value>` out of `refresh_token=<value>; HttpOnly; …`. */
        fun parseRefreshToken(setCookie: String): String? {
            if (!setCookie.startsWith("$COOKIE_NAME=")) return null
            return setCookie.substringAfter("$COOKIE_NAME=").substringBefore(';').takeIf { it.isNotEmpty() }
        }
    }
}
