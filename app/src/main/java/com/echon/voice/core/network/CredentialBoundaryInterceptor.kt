package com.echon.voice.core.network

import okhttp3.Interceptor
import okhttp3.Response

/** Runs on EVERY network hop, including redirects (application interceptors don't). */
class CredentialBoundaryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val safe = if (TlsPinning.isApiOrigin(request.url)) request else request.newBuilder()
            .removeHeader("Authorization")
            .removeHeader("X-Refresh-Token")
            .removeHeader("Cookie")
            .build()
        return chain.proceed(safe)
    }
}
