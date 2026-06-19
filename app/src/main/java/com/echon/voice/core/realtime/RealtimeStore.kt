package com.echon.voice.core.realtime

import com.echon.voice.core.di.ApplicationScope
import com.echon.voice.feature.auth.AuthStore
import com.echon.voice.feature.chat.ChatStores
import com.echon.voice.feature.moderation.BlocksStore
import com.echon.voice.feature.servers.ServersStore
import com.echon.voice.model.ChannelKind
import com.echon.voice.model.ChatChannelKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes WebSocket events into the chat/servers/blocks stores and owns derived
 * realtime UI state (typing, presence, unread). Mirrors the iOS `RealtimeStore`,
 * including the lossy-socket REST reconcile on reconnect and the typing throttle
 * (3s start cadence / 5s idle stop / 8s incoming expiry).
 */
@Singleton
class RealtimeStore @Inject constructor(
    private val ws: WsClient,
    private val chat: ChatStores,
    private val servers: ServersStore,
    private val auth: AuthStore,
    private val blocks: BlocksStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _typingUsers = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val typingUsers: StateFlow<Map<String, Set<String>>> = _typingUsers.asStateFlow()

    private val _presence = MutableStateFlow<Map<String, String>>(emptyMap())
    val presence: StateFlow<Map<String, String>> = _presence.asStateFlow()

    private val _unreadChannelIds = MutableStateFlow<Set<String>>(emptySet())
    val unreadChannelIds: StateFlow<Set<String>> = _unreadChannelIds.asStateFlow()

    @Volatile var openChannelId: String? = null

    private var consumeJob: Job? = null
    private val myId: String? get() = auth.currentUser.value?.id

    init {
        // Tear the socket down on sign-out so it doesn't reconnect with a dead session.
        scope.launch {
            auth.phase.collect { phase ->
                if (phase == AuthStore.Phase.SignedOut) stop()
            }
        }
    }

    private var lastTypingSentAt = 0L
    private var typingStopJob: Job? = null
    private val typingExpiry = ConcurrentHashMap<String, Job>()

    fun start() {
        if (consumeJob != null) return
        consumeJob = scope.launch { ws.events.collect { handle(it) } }
        ws.start()
    }

    suspend fun stop() {
        consumeJob?.cancel()
        consumeJob = null
        ws.stop()
        _isConnected.value = false
        _typingUsers.value = emptyMap()
        _unreadChannelIds.value = emptySet()
    }

    suspend fun joinAllTextChannels() {
        servers.servers.value.forEach { server ->
            servers.channelsFor(server.id).filter { it.type == ChannelKind.TEXT }.forEach { ws.join(it.id) }
        }
    }

    fun channelOpened(channelId: String) {
        openChannelId = channelId
        _unreadChannelIds.update { it - channelId }
        ws.join(channelId)
    }

    fun channelClosed(channelId: String) {
        if (openChannelId == channelId) openChannelId = null
        stopTypingNow(channelId)
    }

    // --- Outgoing typing throttle ---

    fun userIsTyping(channelId: String) {
        val now = System.currentTimeMillis()
        if (now - lastTypingSentAt > 3_000) {
            lastTypingSentAt = now
            ws.sendTyping(channelId, true)
        }
        typingStopJob?.cancel()
        typingStopJob = scope.launch {
            delay(5_000)
            if (isActive) stopTypingNow(channelId)
        }
    }

    private fun stopTypingNow(channelId: String) {
        typingStopJob?.cancel()
        typingStopJob = null
        if (lastTypingSentAt == 0L) return
        lastTypingSentAt = 0L
        ws.sendTyping(channelId, false)
    }

    // --- Event routing ---

    private fun handle(event: WsEvent) {
        when (event) {
            is WsEvent.SocketConnected -> {
                _isConnected.value = true
                scope.launch {
                    joinAllTextChannels()
                    openChannelId?.let { chat.store(it).reconcileLatest() }
                }
            }
            is WsEvent.SocketDisconnected -> _isConnected.value = false
            is WsEvent.MessageNew -> {
                val channelId = event.message.channelId ?: return
                val kind = if (event.message.channelKind == "dm") ChatChannelKind.DM else ChatChannelKind.SERVER
                chat.store(channelId, kind).applyRemote(event.message)
                clearTyping(event.message.author?.id, channelId)
                if (channelId != openChannelId && event.message.author?.id != myId) {
                    _unreadChannelIds.update { it + channelId }
                }
            }
            is WsEvent.MessageUpdated -> event.message.channelId?.let { chat.store(it).applyUpdate(event.message) }
            is WsEvent.MessageDeleted ->
                if (event.channelId != null) chat.store(event.channelId).applyDelete(event.messageId)
                else chat.applyDeleteEverywhere(event.messageId)
            is WsEvent.TypingUpdate -> {
                if (event.userId == myId) return
                if (event.isTyping) {
                    _typingUsers.update { it + (event.channelId to (it[event.channelId].orEmpty() + event.userId)) }
                    scheduleTypingExpiry(event.channelId, event.userId)
                } else {
                    clearTyping(event.userId, event.channelId)
                }
            }
            is WsEvent.PresenceChanged -> _presence.update { it + (event.userId to event.status) }
            is WsEvent.UserBlocked -> blocks.applyRemote(event.userId, true)
            is WsEvent.UserUnblocked -> blocks.applyRemote(event.userId, false)
            is WsEvent.VoiceStateChanged,
            is WsEvent.FriendsChanged,
            is WsEvent.ReadStateUpdated,
            is WsEvent.Ready,
            is WsEvent.Unknown,
            -> Unit
        }
    }

    private fun clearTyping(userId: String?, channelId: String) {
        if (userId == null) return
        _typingUsers.update { it + (channelId to (it[channelId].orEmpty() - userId)) }
        typingExpiry.remove("$channelId/$userId")?.cancel()
    }

    private fun scheduleTypingExpiry(channelId: String, userId: String) {
        val key = "$channelId/$userId"
        typingExpiry.remove(key)?.cancel()
        typingExpiry[key] = scope.launch {
            delay(8_000)
            if (isActive) {
                _typingUsers.update { it + (channelId to (it[channelId].orEmpty() - userId)) }
                typingExpiry.remove(key)
            }
        }
    }
}
