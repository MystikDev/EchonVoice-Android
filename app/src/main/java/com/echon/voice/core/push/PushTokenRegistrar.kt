package com.echon.voice.core.push

import com.echon.voice.core.di.ApplicationScope
import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.apiCall
import com.echon.voice.feature.auth.AuthStore
import com.echon.voice.model.RegisterDeviceRequest
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton

/** Registration failures are visible and retried; account changes cancel pending work. */
@Singleton
class PushTokenRegistrar @Inject constructor(
    private val api: EchonApi,
    private val auth: AuthStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val refresh = MutableStateFlow(0L)
    private val _status = MutableStateFlow(PushRegistrationStatus.SIGNED_OUT)
    val status = _status.asStateFlow()
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(auth.phase, auth.currentUser, refresh) { phase, user, revision ->
                Triple(phase, user?.id, revision)
            }.distinctUntilChanged().collectLatest { (phase, userId, _) ->
                if (phase != AuthStore.Phase.SignedIn || userId == null) {
                    _status.value = PushRegistrationStatus.SIGNED_OUT
                    return@collectLatest
                }
                val messaging = runCatching { FirebaseMessaging.getInstance() }.getOrNull()
                if (messaging == null) {
                    _status.value = PushRegistrationStatus.UNAVAILABLE
                    return@collectLatest
                }
                registerPushWithRetry(
                    fetchToken = {
                        suspendCancellableCoroutine { continuation ->
                            messaging.token.addOnSuccessListener { token ->
                                if (continuation.isActive) continuation.resume(token)
                            }.addOnFailureListener { error ->
                                if (continuation.isActive) continuation.resumeWithException(error)
                            }
                        }
                    },
                    register = { token -> apiCall { api.registerDevice(RegisterDeviceRequest(token, "android")) } },
                    stillSignedIn = { auth.phase.value == AuthStore.Phase.SignedIn && auth.currentUser.value?.id == userId },
                    changed = { _status.value = it },
                )
            }
        }
    }

    /** Retry on foreground return and explicit user request, without logging tokens. */
    fun retry() { refresh.update { it + 1 } }

    @Suppress("UNUSED_PARAMETER")
    fun onTokenRefreshed(token: String) = retry()
}
