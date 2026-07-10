package com.echon.voice.core.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A pending "open this conversation" request from a tapped notification. */
data class ChatDeepLink(
    val channelId: String,
    val channelName: String,
    val channelKind: String,
)

/**
 * Bridges a notification tap (handled in [com.echon.voice.MainActivity]) to the
 * Compose nav graph. The tap [submit]s a target; the signed-in nav host observes
 * [pending] and navigates once it exists (i.e. after sign-in), then [consume]s it.
 * Holding it in a StateFlow lets a cold-start tap wait for bootstrap to finish.
 */
@Singleton
class DeepLinkStore @Inject constructor() {
    private val _pending = MutableStateFlow<ChatDeepLink?>(null)
    val pending: StateFlow<ChatDeepLink?> = _pending.asStateFlow()

    fun submit(link: ChatDeepLink) {
        _pending.value = link
    }

    fun consume() {
        _pending.value = null
    }
}
