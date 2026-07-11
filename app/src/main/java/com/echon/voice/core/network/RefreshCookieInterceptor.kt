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
        val response = chain.proceed(chain.request())
        response.headers("Set-Cookie").forEach { header ->
            parseRefreshToken(header)?.let(session::onRefreshCookie)
        }
        return response
    }

    internal companion object {
        private const val COOKIE_NAME = "refresh_token"

        /** Pull `<value>` out of `refresh_token=<value>; HttpOnly; …`. */
        fun parseRefreshToken(setCookie: String): String? {
            if (!setCookie.startsWith("$COOKIE_NAME=")) return null
            return setCookie.substringAfter("$COOKIE_NAME=").substringBefore(';').takeIf { it.isNotEmpty() }
        }
    }
}
