package com.echon.voice.core.network

import com.echon.voice.core.storage.TokenStorage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the session tokens, shared by the auth interceptor,
 * the 401 authenticator, and the auth store. Mirrors the token state the iOS
 * `APIClient` actor owns. In-memory values are cached for the synchronous OkHttp
 * interceptor/authenticator path and kept in sync with [SecureTokenStore].
 */
@Singleton
class SessionStore @Inject constructor(
    private val storage: TokenStorage,
) {
    @Volatile
    private var access: String? = storage.accessToken

    @Volatile
    private var refresh: String? = storage.refreshToken

    @Volatile var generation: Long = 0
        private set

    private val _unauthorized = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when a 401 could not be recovered (refresh failed) — sign the user out. */
    val unauthorized: SharedFlow<Unit> = _unauthorized

    val accessToken: String? get() = access
    val refreshToken: String? get() = refresh
    val hasSession: Boolean get() = access != null || refresh != null

    @Synchronized
    fun credentials(): SessionCredentials = SessionCredentials(access, refresh, generation)

    /**
     * Adopt a login/register session. A null [refresh] means "the response body
     * carried no refresh token" — which is ALWAYS true (the backend delivers it
     * only as an HttpOnly cookie, captured by [RefreshCookieInterceptor] moments
     * before this runs) — so null must PRESERVE the captured token, not erase
     * it. Nulling it here was the "constantly signed out" bug: the session
     * silently lost its refresh token at login and died with the first expired
     * access token. Ending the session goes through [clear], never this.
     */
    @Synchronized
    fun setTokens(access: String?, refresh: String?) {
        generation++
        this.access = access
        storage.accessToken = access
        if (refresh != null) {
            this.refresh = refresh
            storage.refreshToken = refresh
        }
    }

    /**
     * Absorb the rotating `refresh_token` the backend sets as an HttpOnly cookie
     * on login and refresh. This is the ONLY way the app receives the refresh
     * token — it is not in any response body — so without it the session can't
     * survive the 15-minute access token expiring, and the user is signed out on
     * the next launch. Persisted so it restores across app restarts.
     */
    @Synchronized
    fun onRefreshCookie(token: String, expectedGeneration: Long = generation) {
        if (expectedGeneration != generation) return
        if (token.isEmpty() || token == refresh) return
        refresh = token
        storage.refreshToken = token
    }

    /** Update just the access token (and rotated refresh) after a successful refresh. */
    @Synchronized
    fun updateAfterRefresh(access: String, rotatedRefresh: String?, expectedGeneration: Long = generation): Boolean {
        if (expectedGeneration != generation) return false
        this.access = access
        storage.accessToken = access
        if (rotatedRefresh != null) {
            this.refresh = rotatedRefresh
            storage.refreshToken = rotatedRefresh
        }
        return true
    }

    @Synchronized
    fun clearIfCurrent(expectedGeneration: Long): Boolean {
        if (expectedGeneration != generation) return false
        clear()
        return true
    }

    @Synchronized
    fun clear() {
        generation++
        access = null
        refresh = null
        storage.clear()
    }

    fun signalUnauthorized() {
        _unauthorized.tryEmit(Unit)
    }
}

data class SessionCredentials(val access: String?, val refresh: String?, val generation: Long)
