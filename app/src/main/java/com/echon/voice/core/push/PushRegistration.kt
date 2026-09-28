package com.echon.voice.core.push

import com.echon.voice.core.network.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

enum class PushRegistrationStatus { SIGNED_OUT, REGISTERING, REGISTERED, FAILED, UNAVAILABLE }

internal suspend fun registerPushWithRetry(
    fetchToken: suspend () -> String,
    register: suspend (String) -> Unit,
    stillSignedIn: () -> Boolean,
    changed: (PushRegistrationStatus) -> Unit,
) {
    changed(PushRegistrationStatus.REGISTERING)
    for (attempt in 0..2) {
        if (!stillSignedIn()) return
        try {
            val token = withTimeoutOrNull(20_000) { fetchToken() } ?: throw IOException("Push token request timed out")
            currentCoroutineContext().ensureActive()
            if (!stillSignedIn()) return
            require(token.isNotBlank())
            register(token)
            currentCoroutineContext().ensureActive()
            if (stillSignedIn()) changed(PushRegistrationStatus.REGISTERED)
            return
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (!stillSignedIn()) return
            // Authentication/contract errors need user/server intervention, not a loop.
            val retryable = e !is ApiException.Unauthorized &&
                (e !is ApiException.Http || e.status == 429 || e.status >= 500)
            if (!retryable || attempt == 2) {
                changed(PushRegistrationStatus.FAILED)
                return
            }
            delay(if (attempt == 0) 1_000 else 5_000)
        }
    }
}
