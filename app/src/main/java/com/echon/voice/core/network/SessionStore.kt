package com.echon.voice.core.network

import com.echon.voice.core.storage.SecureTokenStore
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
    private val storage: SecureTokenStore,
) {
    @Volatile
    private var access: String? = storage.accessToken

    @Volatile
    private var refresh: String? = storage.refreshToken

    private val _unauthorized = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when a 401 could not be recovered (refresh failed) — sign the user out. */
    val unauthorized: SharedFlow<Unit> = _unauthorized

    val accessToken: String? get() = access
    val refreshToken: String? get() = refresh
    val hasSession: Boolean get() = access != null || refresh != null

    @Synchronized
    fun setTokens(access: String?, refresh: String?) {
        this.access = access
        this.refresh = refresh
        storage.accessToken = access
        storage.refreshToken = refresh
    }

    /** Update just the access token (and rotated refresh) after a successful refresh. */
    @Synchronized
    fun updateAfterRefresh(access: String, rotatedRefresh: String?) {
        this.access = access
        storage.accessToken = access
        if (rotatedRefresh != null) {
            this.refresh = rotatedRefresh
            storage.refreshToken = rotatedRefresh
        }
    }

    @Synchronized
    fun clear() {
        access = null
        refresh = null
        storage.clear()
    }

    fun signalUnauthorized() {
        _unauthorized.tryEmit(Unit)
    }
}
