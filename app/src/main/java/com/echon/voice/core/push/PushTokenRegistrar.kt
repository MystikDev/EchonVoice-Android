package com.echon.voice.core.push

import android.util.Log
import com.echon.voice.core.di.ApplicationScope
import com.echon.voice.core.network.ApiException
import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.apiCall
import com.echon.voice.feature.auth.AuthStore
import com.echon.voice.model.RegisterDeviceRequest
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers this install's FCM token with the backend so it can receive push
 * notifications. The token is user-scoped, so registration only happens while
 * signed in: on each sign-in we fetch the current token and POST it, and
 * [onTokenRefreshed] re-registers when FCM rotates the token.
 *
 * Degrades gracefully: if Firebase isn't configured (no google-services.json) or
 * the backend `/v1/devices` endpoint isn't live yet, this no-ops without crashing.
 */
@Singleton
class PushTokenRegistrar @Inject constructor(
    private val api: EchonApi,
    private val auth: AuthStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** Begin watching auth state; call once from the Application. */
    fun start() {
        scope.launch {
            auth.phase.collectLatest { phase ->
                if (phase == AuthStore.Phase.SignedIn) fetchAndRegister()
            }
        }
    }

    /** Called by [EchonMessagingService.onNewToken] when FCM rotates the token. */
    fun onTokenRefreshed(token: String) {
        if (auth.phase.value == AuthStore.Phase.SignedIn) {
            scope.launch { register(token) }
        }
    }

    private fun fetchAndRegister() {
        // getInstance() throws if no default FirebaseApp (Firebase not configured);
        // swallow so the app runs normally until a Firebase project is set up.
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> scope.launch { register(token) } }
                .addOnFailureListener { Log.w(TAG, "FCM token fetch failed", it) }
        }.onFailure { Log.i(TAG, "Push disabled (Firebase not configured)") }
    }

    private suspend fun register(token: String) {
        try {
            apiCall { api.registerDevice(RegisterDeviceRequest(deviceToken = token, platform = "android")) }
            Log.i(TAG, "device token registered")
        } catch (e: ApiException.Unauthorized) {
            // Signed out between the check and the call; ignore.
        } catch (e: Exception) {
            Log.w(TAG, "device token registration failed", e)
        }
    }

    private companion object {
        const val TAG = "EchonPush"
    }
}
