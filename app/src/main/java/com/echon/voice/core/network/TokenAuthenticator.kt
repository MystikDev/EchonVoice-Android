package com.echon.voice.core.network

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recovers from 401s by refreshing the access token, mirroring the iOS
 * `APIClient` refresh-on-401 with `refreshInFlight` serialization:
 *
 *  - Only one refresh runs at a time (`@Synchronized`); concurrent 401s that
 *    arrive after a successful refresh simply retry with the new token.
 *  - The refresh token rotates each call and is persisted via [SessionStore].
 *  - A request is retried at most once (guarded by [priorResponseCount]); if the
 *    refresh fails, the session is cleared and an unauthorized signal is emitted
 *    so the app can sign out.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val session: SessionStore,
    private val refresher: TokenRefresher,
) : Authenticator {

    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        // Only ever refresh + retry with credentials against the Echon API. Coil
        // and any absolute media URL can 401 from a third-party host; without this
        // guard OkHttp would re-send the request WITH the victim's bearer attached.
        if (!TlsPinning.isApiHost(response.request.url.host)) return null

        // Give up after one retry to avoid infinite 401 loops.
        if (priorResponseCount(response) >= 2) return null

        val failedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
        val current = session.accessToken

        // Another thread already refreshed while we were blocked — retry as-is.
        if (current != null && current != failedToken) {
            return response.request.newBuilder()
                .header("Authorization", "Bearer $current")
                .build()
        }

        val refreshToken = session.refreshToken ?: run {
            session.clear()
            session.signalUnauthorized()
            return null
        }

        val refreshed = refresher.refresh(refreshToken)
        if (refreshed == null) {
            session.clear()
            session.signalUnauthorized()
            return null
        }

        session.updateAfterRefresh(refreshed.token, refreshed.refreshToken)
        return response.request.newBuilder()
            .header("Authorization", "Bearer ${refreshed.token}")
            .build()
    }

    private fun priorResponseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
