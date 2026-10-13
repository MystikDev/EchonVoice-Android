package com.echon.voice.core.realtime

import com.echon.voice.core.di.ApplicationScope
import com.echon.voice.feature.auth.AuthStore
import com.echon.voice.feature.chat.ChatStores
import com.echon.voice.feature.dms.DMsStore
import com.echon.voice.feature.friends.FriendsStore
import com.echon.voice.feature.moderation.BlocksStore
import com.echon.voice.feature.servers.ServersStore
import com.echon.voice.feature.voice.VoiceStore
import com.echon.voice.model.ChannelKind
import com.echon.voice.model.ChatChannelKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
    private val presenceStore: PresenceStore,
    private val chat: ChatStores,
    private val servers: ServersStore,
    private val auth: AuthStore,
    private val blocks: BlocksStore,
    private val dms: DMsStore,
    private val friends: FriendsStore,
    private val voice: VoiceStore,
    private val calls: com.echon.voice.feature.voice.VoiceCallStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _typingUsers = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val typingUsers: StateFlow<Map<String, Set<String>>> = _typingUsers.asStateFlow()

    val presence = presenceStore.social

    private val _unreadChannelIds = MutableStateFlow<Set<String>>(emptySet())
    val unreadChannelIds: StateFlow<Set<String>> = _unreadChannelIds.asStateFlow()

    @Volatile var openChannelId: String? = null

    private var channelAccessJob: Job? = null
    private var consumeJob: Job? = null
    private var reconcileJob: Job? = null
    private val myId: String? get() = auth.currentUser.value?.id

    private var lastTypingSentAt = 0L
    private var typingStopJob: Job? = null
    private val typingExpiry = ConcurrentHashMap<String, Job>()

    fun start() {
        if (consumeJob != null) return
        channelAccessJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var previous = emptySet<String>()
            servers.revokedChannelIds.collect { revoked ->
                val newlyRevoked = revoked - previous
                previous = revoked
                newlyRevoked.forEach { purgeChannel(it) }
            }
        }
        consumeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { ws.events.collect { handle(it) } }
        ws.start()
    }

    suspend fun stop() {
        consumeJob?.cancelAndJoin()
        consumeJob = null
        channelAccessJob?.cancelAndJoin()
        channelAccessJob = null
        reconcileJob?.cancelAndJoin()
        reconcileJob = null
        ws.stop()
        _isConnected.value = false
        typingStopJob?.cancel()
        typingStopJob = null
        typingExpiry.values.forEach { it.cancel() }
        typingExpiry.clear()
        lastTypingSentAt = 0L
        openChannelId = null
        presenceStore.disconnect()
        _typingUsers.value = emptyMap()
        _unreadChannelIds.value = emptySet()
    }

    suspend fun joinAllTextChannels() {
        servers.servers.value.forEach { server ->
            servers.channelsFor(server.id).filter { it.type == ChannelKind.TEXT }.forEach { ws.join(it.id) }
        }
    }

    fun channelOpened(channelId: String) {
        if (channelId in servers.revokedChannelIds.value) return
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
        if (channelId in servers.revokedChannelIds.value) return
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
                reconcileJob?.cancel()
                reconcileJob = scope.launch {
                    joinAllTextChannels()
                    try { openChannelId?.let { chat.store(it).reconcileLatest() } }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { /* Permission loss clears the store; transient failure can retry. */ }
                }
            }
            is WsEvent.SocketDisconnected -> {
                _isConnected.value = false
                presenceStore.disconnect()
            }
            // Ready follows server registration; querying on TCP open can predate presence.
            is WsEvent.Ready -> {
                presenceStore.connect()
                reconcileJob?.cancel()
                reconcileJob = scope.launch {
                    try {
                        servers.loadServers()
                        servers.servers.value.forEach { servers.loadChannels(it.id) }
                        joinAllTextChannels()
                        voice.refresh()
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { /* Next Ready or selection reconciles again. */ }
                }
            }
            is WsEvent.ChannelUpserted -> {
                servers.upsertChannel(event.channel)
                if (event.channel.type == ChannelKind.TEXT) ws.join(event.channel.id)
                voice.refresh()
            }
            is WsEvent.ChannelDeleted -> removeChannel(event.channelId)
            is WsEvent.MessageNew -> {
                val channelId = event.message.channelId ?: return
                if (channelId in servers.revokedChannelIds.value) return
                val kind = if (event.message.channelKind == "dm") ChatChannelKind.DM else ChatChannelKind.SERVER
                chat.store(channelId, kind).applyRemote(event.message)
                clearTyping(event.message.author?.id, channelId)
                if (kind == ChatChannelKind.DM) dms.touch(channelId, event.message.createdAt)
                if (channelId != openChannelId && event.message.author?.id != myId) {
                    _unreadChannelIds.update { it + channelId }
                }
            }
            is WsEvent.MessageUpdated -> event.message.channelId?.takeUnless { it in servers.revokedChannelIds.value }?.let { chat.store(it).applyUpdate(event.message) }
            is WsEvent.MessageDeleted ->
                if (event.channelId != null) chat.store(event.channelId).applyDelete(event.messageId)
                else chat.applyDeleteEverywhere(event.messageId)
            is WsEvent.TypingUpdate -> {
                if (event.channelId in servers.revokedChannelIds.value) return
                if (event.userId == myId) return
                if (event.isTyping) {
                    _typingUsers.update { it + (event.channelId to (it[event.channelId].orEmpty() + event.userId)) }
                    scheduleTypingExpiry(event.channelId, event.userId)
                } else {
                    clearTyping(event.userId, event.channelId)
                }
            }
            is WsEvent.PresenceChanged -> presenceStore.apply(event.userId, event.status)
            is WsEvent.UserBlocked -> blocks.applyRemote(event.userId, true)
            is WsEvent.UserUnblocked -> blocks.applyRemote(event.userId, false)
            is WsEvent.FriendsChanged -> scope.launch { runCatching { friends.load() } }
            is WsEvent.VoiceStateChanged -> voice.refresh()
            is WsEvent.ReadStateUpdated,
            is WsEvent.Unknown,
            -> Unit
        }
    }

    private fun removeChannel(channelId: String) { servers.removeChannel(channelId) }

    private fun purgeChannel(channelId: String) {
        if (openChannelId == channelId) channelClosed(channelId)
        ws.leave(channelId)
        chat.revoke(channelId)
        calls.revokeChannel(channelId)
        voice.removeChannel(channelId)
        _typingUsers.update { it - channelId }
        _unreadChannelIds.update { it - channelId }
        typingExpiry.keys.filter { it.startsWith("$channelId/") }.forEach { typingExpiry.remove(it)?.cancel() }
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
