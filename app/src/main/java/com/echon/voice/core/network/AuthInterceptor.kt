package com.echon.voice.core.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches `Authorization: Bearer <access>` to outbound requests when a session
 * exists. The refresh endpoint authenticates with `X-Refresh-Token` instead and
 * opts out via the [NO_AUTH_HEADER] marker so a stale bearer never shadows it.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val session: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(NO_AUTH_HEADER) != null) {
            return chain.proceed(request.newBuilder().removeHeader(NO_AUTH_HEADER).build())
        }
        val token = session.accessToken
        val authed = if (token != null) {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            request
        }
        return chain.proceed(authed)
    }

    companion object {
        /** Internal header: presence opts a request out of the bearer attach. */
        const val NO_AUTH_HEADER = "X-Echon-No-Auth"
    }
}
