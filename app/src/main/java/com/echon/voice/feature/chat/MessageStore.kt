package com.echon.voice.feature.chat

import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.apiCall
import com.echon.voice.model.ChatChannelKind
import com.echon.voice.model.Message
import com.echon.voice.model.OutgoingAttachment
import com.echon.voice.model.Reaction
import com.echon.voice.model.SendMessageRequest
import com.echon.voice.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-channel message list with optimistic send + WS-echo dedup, cursor
 * pagination, reactions/pins/edit/delete, and lossy-socket REST reconcile —
 * the Android port of the iOS `MessageStore`. Messages are ordered oldest→newest.
 */
class MessageStore(
    private val api: EchonApi,
    val channelId: String,
    val kind: ChatChannelKind,
) {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _loadingOlder = MutableStateFlow(false)
    val loadingOlder: StateFlow<Boolean> = _loadingOlder.asStateFlow()

    @Volatile var hasMoreHistory = true
        private set

    private var loadingInitial = false

    private suspend fun fetch(before: String?): List<Message> =
        if (kind == ChatChannelKind.DM) {
            apiCall { api.dmMessages(channelId, before, PAGE) }.messages
        } else {
            apiCall { api.channelMessages(channelId, before, PAGE) }.messages
        }

    suspend fun loadInitial() {
        if (_messages.value.isNotEmpty() || loadingInitial) return
        loadingInitial = true
        try {
            val page = fetch(before = null)
            _messages.value = page
            hasMoreHistory = page.size >= PAGE
        } finally {
            loadingInitial = false
        }
    }

    suspend fun loadOlder() {
        if (!hasMoreHistory || _loadingOlder.value || loadingInitial) return
        val oldest = _messages.value.firstOrNull { !it.isLocalEcho } ?: return
        _loadingOlder.value = true
        try {
            val page = fetch(before = oldest.id)
            val known = _messages.value.mapTo(HashSet()) { it.id }
            _messages.update { page.filter { m -> m.id !in known } + it }
            hasMoreHistory = page.size >= PAGE
        } finally {
            _loadingOlder.value = false
        }
    }

    /** Refetch the latest page after a (lossy) reconnect and merge it in. */
    suspend fun reconcileLatest() {
        if (_messages.value.isEmpty()) {
            loadInitial()
            return
        }
        runCatching { fetch(before = null) }.getOrNull()?.forEach { applyRemote(it) }
    }

    suspend fun send(
        content: String,
        author: User?,
        replyToId: String? = null,
        attachments: List<OutgoingAttachment>? = null,
    ) {
        val trimmed = content.trim()
        if (trimmed.isEmpty() && attachments.isNullOrEmpty()) return

        val localId = "local-${UUID.randomUUID()}"
        val echo = Message(
            id = localId,
            channelId = channelId,
            content = trimmed,
            author = author,
            replyToId = replyToId,
            createdAt = Instant.now().toString(),
            attachments = attachments?.map {
                com.echon.voice.model.Attachment(it.url, it.filename, it.mimetype, it.size, it.url)
            },
            isLocalEcho = true,
        )
        _messages.update { it + echo }

        try {
            val sent = if (kind == ChatChannelKind.DM) {
                apiCall { api.sendDmMessage(channelId, SendMessageRequest(trimmed, replyToId, attachments)) }
            } else {
                apiCall { api.sendChannelMessage(channelId, SendMessageRequest(trimmed, replyToId, attachments)) }
            }
            _messages.update { list ->
                val idx = list.indexOfFirst { it.id == localId }
                when {
                    idx >= 0 -> list.toMutableList().also { it[idx] = sent }
                    list.none { it.id == sent.id } -> list + sent
                    else -> list
                }
            }
        } catch (e: Exception) {
            _messages.update { list ->
                val idx = list.indexOfFirst { it.id == localId }
                if (idx >= 0) list.toMutableList().also { it[idx] = list[idx].copy(sendFailed = true) } else list
            }
        }
    }

    suspend fun retry(localId: String, author: User?) {
        val failed = _messages.value.firstOrNull { it.id == localId } ?: return
        _messages.update { list -> list.filterNot { it.id == localId } }
        send(
            content = failed.content.orEmpty(),
            author = author,
            replyToId = failed.replyToId,
            attachments = failed.attachments?.map {
                OutgoingAttachment(it.filename ?: "file", it.url ?: "", it.mimetype ?: "", it.size ?: 0)
            }?.takeIf { it.isNotEmpty() },
        )
    }

    fun discard(localId: String) {
        _messages.update { list -> list.filterNot { it.id == localId } }
    }

    suspend fun edit(messageId: String, content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        val previous = _messages.value.firstOrNull { it.id == messageId } ?: return
        _messages.update { list ->
            list.map { if (it.id == messageId) it.copy(content = trimmed, editedAt = Instant.now().toString()) else it }
        }
        runCatching { apiCall { api.editMessage(messageId, com.echon.voice.model.EditMessageRequest(trimmed)) } }
            .onFailure { applyUpdate(previous) }
    }

    suspend fun delete(messageId: String) {
        val removed = _messages.value.firstOrNull { it.id == messageId }
        applyDelete(messageId)
        runCatching { apiCall { api.deleteMessage(messageId) } }
            .onFailure { if (removed != null) applyRemote(removed) }
    }

    suspend fun setPinned(messageId: String, pinned: Boolean) {
        _messages.update { list -> list.map { if (it.id == messageId) it.copy(isPinned = pinned) else it } }
        runCatching {
            if (pinned) apiCall { api.pinMessage(messageId) } else apiCall { api.unpinMessage(messageId) }
        }.onFailure { reconcileLatest() }
    }

    suspend fun toggleReaction(messageId: String, emoji: String, myUserId: String?) {
        if (myUserId == null) return
        val msg = _messages.value.firstOrNull { it.id == messageId } ?: return
        val mine = msg.reactions?.firstOrNull { it.emojiKey == emoji }?.includes(myUserId) == true

        val updated = msg.reactions.orEmpty().toMutableList()
        val ridx = updated.indexOfFirst { it.emojiKey == emoji }
        if (mine) {
            if (ridx >= 0) {
                val r = updated[ridx]
                val ids = (r.userIds ?: emptyList()) - myUserId
                if (ids.isEmpty()) updated.removeAt(ridx)
                else updated[ridx] = r.copy(userIds = ids, count = ids.size)
            }
        } else {
            if (ridx >= 0) {
                val r = updated[ridx]
                val ids = (r.userIds ?: emptyList()) + myUserId
                updated[ridx] = r.copy(userIds = ids, count = ids.size)
            } else {
                updated.add(Reaction(emojiKey = emoji, count = 1, userIds = listOf(myUserId)))
            }
        }
        _messages.update { list -> list.map { if (it.id == messageId) it.copy(reactions = updated) else it } }

        runCatching {
            if (mine) apiCall { api.removeReaction(messageId, emoji) } else apiCall { api.addReaction(messageId, emoji) }
        }.onFailure { reconcileLatest() }
    }

    // --- Realtime merges ---

    fun applyRemote(incoming: Message) {
        _messages.update { list ->
            val byId = list.indexOfFirst { it.id == incoming.id }
            when {
                byId >= 0 -> list.toMutableList().also { it[byId] = incoming }
                else -> {
                    val echoIdx = list.indexOfFirst {
                        it.isLocalEcho && it.author?.id == incoming.author?.id && it.content == incoming.content
                    }
                    if (echoIdx >= 0) list.toMutableList().also { it[echoIdx] = incoming } else list + incoming
                }
            }
        }
    }

    fun applyUpdate(incoming: Message) {
        _messages.update { list -> list.map { if (it.id == incoming.id) incoming else it } }
    }

    fun applyDelete(messageId: String) {
        _messages.update { list -> list.filterNot { it.id == messageId } }
    }

    fun message(withId: String?): Message? = withId?.let { id -> _messages.value.firstOrNull { it.id == id } }

    private companion object {
        const val PAGE = 50
    }
}

/** Per-channel store registry so chat state survives navigation (iOS `ChatStores`). */
@Singleton
class ChatStores @Inject constructor(
    private val api: EchonApi,
) {
    private val byChannel = ConcurrentHashMap<String, MessageStore>()

    fun store(channelId: String, kind: ChatChannelKind = ChatChannelKind.SERVER): MessageStore =
        byChannel.getOrPut(channelId) { MessageStore(api, channelId, kind) }

    fun applyDeleteEverywhere(messageId: String) {
        byChannel.values.forEach { it.applyDelete(messageId) }
    }
}
