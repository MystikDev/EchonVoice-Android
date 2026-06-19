package com.echon.voice.feature.auth

import com.echon.voice.core.di.ApplicationScope
import com.echon.voice.core.network.ApiException
import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.SessionStore
import com.echon.voice.core.network.apiCall
import com.echon.voice.model.LoginRequest
import com.echon.voice.model.LoginResponse
import com.echon.voice.model.RegisterRequest
import com.echon.voice.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the authentication phase and current user — the Android analog of the iOS
 * `AuthStore`. A singleton so the whole app observes one source of truth; the
 * root composable routes on [phase].
 */
@Singleton
class AuthStore @Inject constructor(
    private val api: EchonApi,
    private val session: SessionStore,
    @ApplicationScope scope: CoroutineScope,
) {
    enum class Phase { Loading, SignedOut, NeedsEula, SignedIn }

    private val _phase = MutableStateFlow(Phase.Loading)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        // A 401 that refresh couldn't recover signs the user out everywhere.
        scope.launch { session.unauthorized.collect { signOut() } }
    }

    /**
     * Launch bootstrap: restore tokens and resolve /v1/me. Access tokens live
     * only 15 minutes, so the refresh token is what actually keeps a user signed
     * in — the [com.echon.voice.core.network.TokenAuthenticator] refreshes on 401.
     */
    suspend fun bootstrap() {
        if (!session.hasSession) {
            _phase.value = Phase.SignedOut
            return
        }
        refreshMe()
    }

    /** @throws ApiException on failure (surfaced by the caller's screen). */
    suspend fun logIn(email: String, password: String) {
        val response = apiCall { api.login(LoginRequest(email = email, password = password)) }
        storeSession(response)
        refreshMe()
    }

    /**
     * Registration responds with a live session; logging in again would be
     * refused until the email is verified, so adopt this session directly.
     */
    suspend fun register(email: String, username: String, password: String, dateOfBirth: String) {
        val response = apiCall {
            api.register(RegisterRequest(email = email, username = username, password = password, dateOfBirth = dateOfBirth))
        }
        storeSession(response)
        refreshMe()
    }

    /** Adopt the updated user returned by PATCH /v1/me/profile. */
    fun applyUpdatedUser(user: User) {
        _currentUser.value = user
    }

    suspend fun acceptTos() {
        apiCall { api.acceptTos() }
        _currentUser.value = _currentUser.value?.copy(tosAccepted = true)
        _phase.value = Phase.SignedIn
    }

    suspend fun signOut() {
        runCatching { apiCall { api.logout() } }
        session.clear()
        _currentUser.value = null
        _phase.value = Phase.SignedOut
    }

    private fun storeSession(response: LoginResponse) {
        session.setTokens(access = response.token, refresh = response.refreshToken)
    }

    private suspend fun refreshMe() {
        try {
            val me = apiCall { api.me() }
            _currentUser.value = me.user
            _phase.value = if (me.user.tosAccepted == true) Phase.SignedIn else Phase.NeedsEula
        } catch (e: ApiException.Unauthorized) {
            session.clear()
            _phase.value = Phase.SignedOut
        }
    }
}
