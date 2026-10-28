package com.echon.voice.feature.chat

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.apiCall
import com.echon.voice.core.realtime.RealtimeStore
import com.echon.voice.feature.auth.AuthStore
import com.echon.voice.feature.dms.DMsStore
import com.echon.voice.feature.moderation.BlocksStore
import com.echon.voice.model.ChatChannelKind
import com.echon.voice.model.Message
import com.echon.voice.core.util.readBytesCapped
import com.echon.voice.model.OutgoingAttachment
import com.echon.voice.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val chatStores: ChatStores,
    private val realtime: RealtimeStore,
    private val auth: AuthStore,
    private val blocks: BlocksStore,
    private val api: EchonApi,
    private val servers: com.echon.voice.feature.servers.ServersStore,
    private val dms: DMsStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val channelId: String = checkNotNull(savedState["channelId"])
    val channelName: String = savedState["channelName"] ?: "channel"
    private val kind: ChatChannelKind =
        if (savedState.get<String>("channelKind") == "dm") ChatChannelKind.DM else ChatChannelKind.SERVER

    private val store: MessageStore = chatStores.store(channelId, kind)
    val currentUserId: String? get() = auth.currentUser.value?.id

    /**
     * Messages rendered through the block filter — blocking removes content
     * instantly. Beyond dropping a blocked author's own messages, we also strip
     * their reaction contributions so a blocked user can't surface via a
     * reaction count; reply previews are filtered in [repliedTo].
     */
    val visibleMessages: StateFlow<List<Message>> =
        combine(store.messages, blocks.blockedIds) { messages, blocked ->
            messages
                .filter { it.author?.id !in blocked }
                .map { it.withoutBlockedReactions(blocked) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accessRevoked = combine(servers.revokedChannelIds, store.accessRevoked) { channels, denied -> denied || channelId in channels }

    val loadingOlder: StateFlow<Boolean> = store.loadingOlder

    val typingNames: StateFlow<List<String>> =
        realtime.typingUsers.map { map ->
            (map[channelId] ?: emptySet()).map { id ->
                store.messages.value.lastOrNull { it.author?.id == id }?.author?.username ?: "Someone"
            }.sorted()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var loadError by mutableStateOf<String?>(null)
        private set

    var draft by mutableStateOf("")
    var replyTarget by mutableStateOf<Message?>(null)
        private set
    var editTarget by mutableStateOf<Message?>(null)
        private set
    var pending by mutableStateOf<List<OutgoingAttachment>>(emptyList())
        private set
    var uploading by mutableStateOf(false)
        private set

    private var mentionCandidates: List<MentionCandidate> = emptyList()
    private var currentMention: ActiveMention? = null
    private var messageRefreshJob: Job? = null
    var mentionSuggestions by mutableStateOf<List<MentionCandidate>>(emptyList())
        private set
    var mentionMenuVisible by mutableStateOf(false)
        private set
    var mentionLoading by mutableStateOf(false)
        private set

    fun onEnter() {
        if (channelId in servers.revokedChannelIds.value) return
        realtime.channelOpened(channelId)
        loadMessages()
        if (mentionCandidates.isEmpty()) loadMentionCandidates()
        messageRefreshJob?.cancel()
        messageRefreshJob = viewModelScope.launch {
            // WebSocket delivery is intentionally lossy under server backpressure.
            // While this conversation is visible, REST is the bounded fallback so
            // a missed frame cannot leave the UI stale until process restart.
            while (isActive) {
                delay(MESSAGE_REFRESH_MS)
                try { store.reconcileLatest() }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { /* The next interval or socket event retries. */ }
            }
        }
    }

    fun onLeave() {
        messageRefreshJob?.cancel()
        messageRefreshJob = null
        realtime.channelClosed(channelId)
    }

    fun onDraftChange(text: String) {
        draft = text
        updateMentionSuggestions()
        if (text.isNotEmpty()) realtime.userIsTyping(channelId)
    }

    fun selectMention(candidate: MentionCandidate) {
        val mention = currentMention ?: return
        val username = candidate.user.username ?: return
        draft = replaceActiveMention(draft, mention, username)
        currentMention = null
        mentionMenuVisible = false
        mentionSuggestions = emptyList()
        realtime.userIsTyping(channelId)
    }

    fun retryLoad() { if (store.messages.value.isEmpty()) loadMessages() else loadOlder() }

    fun loadMessages() = viewModelScope.launch {
        loadError = null
        try {
            // Unlike loadInitial(), reconcileLatest() also refreshes a cached
            // conversation when the user returns to it after a missed WS frame.
            store.reconcileLatest()
            if (mentionCandidates.isEmpty()) setMentionCandidates(fallbackMentionCandidates())
        }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { loadError = "Couldn't load messages. Please retry." }
    }

    fun loadOlder() = viewModelScope.launch {
        try { store.loadOlder() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { loadError = "Couldn't load older messages. Please retry." }
    }

    fun send() {
        if (channelId in servers.revokedChannelIds.value) return
        val edit = editTarget
        if (edit != null) {
            val text = draft
            viewModelScope.launch { store.edit(edit.id, text) }
            clearContext()
            draft = ""
            dismissMentionMenu()
            return
        }
        if (draft.isBlank() && pending.isEmpty()) return
        val text = draft
        val reply = replyTarget?.id
        val attachments = pending.takeIf { it.isNotEmpty() }
        draft = ""
        dismissMentionMenu()
        replyTarget = null
        pending = emptyList()
        viewModelScope.launch { store.send(text, auth.currentUser.value, reply, attachments) }
    }

    fun startReply(message: Message) { editTarget = null; replyTarget = message }
    fun startEdit(message: Message) {
        replyTarget = null
        editTarget = message
        draft = message.content.orEmpty()
        updateMentionSuggestions()
    }
    fun clearContext() {
        replyTarget = null
        if (editTarget != null) draft = ""
        editTarget = null
        updateMentionSuggestions()
    }

    fun react(message: Message, emoji: String) =
        viewModelScope.launch { store.toggleReaction(message.id, emoji, currentUserId) }

    fun togglePin(message: Message) =
        viewModelScope.launch { store.setPinned(message.id, message.isPinned != true) }

    fun delete(message: Message) = viewModelScope.launch { store.delete(message.id) }
    fun retry(message: Message) = viewModelScope.launch { store.retry(message.id, auth.currentUser.value) }
    fun discard(message: Message) = store.discard(message.id)

    suspend fun block(user: User) = blocks.block(user)

    /**
     * The message a reply points at, or null if its author is blocked — so a
     * blocked user's username/content can't leak through a reply preview (the
     * preview then falls back to the neutral "Original message" placeholder).
     */
    fun repliedTo(message: Message): Message? =
        store.message(withId = message.replyToId)?.takeUnless { blocks.isBlocked(it.author?.id) }

    fun isMine(message: Message): Boolean = message.author?.id == currentUserId

    /**
     * Creates a FileProvider URI the system camera app can write a photo into.
     * The captured file lands in a dedicated cache subdir; on capture success the
     * caller feeds this URI back through [attach], reusing the upload path.
     */
    fun newCameraCaptureUri(): Uri {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "capture-${System.nanoTime()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun attach(uris: List<Uri>) {
        if (channelId in servers.revokedChannelIds.value) return
        if (uris.isEmpty()) return
        viewModelScope.launch {
            uploading = true
            try {
                val resolver = context.contentResolver
                val results = withContext(Dispatchers.IO) {
                    uris.take(10 - pending.size).mapNotNull { uri ->
                        // Cap the actual streamed bytes so a huge/malicious file can't OOM the app.
                        val bytes = resolver.openInputStream(uri)?.use {
                            it.readBytesCapped(com.echon.voice.core.util.UploadLimits.ATTACHMENT_MAX_BYTES)
                        } ?: return@mapNotNull null
                        val mime = resolver.getType(uri) ?: "application/octet-stream"
                        val ext = mime.substringAfter('/', "bin")
                        val name = "photo-${System.nanoTime()}.$ext"
                        val part = MultipartBody.Part.createFormData(
                            "file", name, bytes.toRequestBody(mime.toMediaTypeOrNull()),
                        )
                        val purpose = "message".toRequestBody("text/plain".toMediaTypeOrNull())
                        val response = apiCall { api.upload(part, purpose) }
                        OutgoingAttachment(name, response.url, response.mimetype ?: mime, response.size ?: bytes.size)
                    }
                }
                if (channelId !in servers.revokedChannelIds.value) pending = pending + results
            } catch (e: Exception) {
                // Surface nothing fatal; failed uploads simply don't attach.
            } finally {
                uploading = false
            }
        }
    }

    fun removePending(attachment: OutgoingAttachment) {
        pending = pending.filterNot { it.url == attachment.url }
    }

    private fun loadMentionCandidates() = viewModelScope.launch {
        mentionLoading = true
        try {
            val users = when (kind) {
                ChatChannelKind.SERVER -> {
                    val serverId = servers.channelsByServer.value.entries.firstNotNullOfOrNull { (id, channels) ->
                        channels.firstOrNull { it.id == channelId }?.let { it.serverId ?: id }
                    }
                    serverId?.let { apiCall { api.serverMembers(it) }.members }
                        ?.map { MentionCandidate(it.user, it.displayName) }
                        .orEmpty()
                }
                ChatChannelKind.DM -> {
                    var conversation = dms.conversations.value.firstOrNull { it.id == channelId }
                    if (conversation == null) {
                        runCatching { dms.load() }
                        conversation = dms.conversations.value.firstOrNull { it.id == channelId }
                    }
                    conversation?.participants.orEmpty().map { MentionCandidate(it, it.username ?: "Unknown") }
                }
            }
            setMentionCandidates(users.ifEmpty { fallbackMentionCandidates() })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            setMentionCandidates(fallbackMentionCandidates())
        } finally {
            mentionLoading = false
            updateMentionSuggestions()
        }
    }

    private fun fallbackMentionCandidates(): List<MentionCandidate> =
        (store.messages.value.mapNotNull { it.author } + listOfNotNull(auth.currentUser.value))
            .distinctBy { it.id }
            .map { MentionCandidate(it, it.username ?: "Unknown") }

    private fun setMentionCandidates(candidates: List<MentionCandidate>) {
        mentionCandidates = candidates
            .filter { it.user.username?.isNotBlank() == true && !blocks.isBlocked(it.user.id) }
            .distinctBy { it.user.id }
            .sortedBy { it.displayName.lowercase() }
        updateMentionSuggestions()
    }

    private fun updateMentionSuggestions() {
        val mention = activeMention(draft)
        currentMention = mention
        mentionMenuVisible = mention != null
        if (mention == null) {
            mentionSuggestions = emptyList()
            return
        }
        val query = mention.query.lowercase()
        mentionSuggestions = mentionCandidates.asSequence()
            .filter { candidate ->
                query.isEmpty() ||
                    candidate.displayName.contains(query, ignoreCase = true) ||
                    candidate.user.username.orEmpty().contains(query, ignoreCase = true)
            }
            .take(8)
            .toList()
    }

    private fun dismissMentionMenu() {
        currentMention = null
        mentionMenuVisible = false
        mentionSuggestions = emptyList()
    }

    private companion object {
        const val MESSAGE_REFRESH_MS = 15_000L
    }
}

data class MentionCandidate(
    val user: User,
    val displayName: String,
)

/**
 * Recomputes reaction tallies to exclude blocked users, so a blocked user can't
 * remain visible via a reaction count. Reactions whose users are all blocked
 * drop out entirely; reactions the server didn't itemize (null `userIds`) are
 * left untouched — they can't be attributed and carry no author content.
 */
internal fun Message.withoutBlockedReactions(blocked: Set<String>): Message {
    val current = reactions
    if (blocked.isEmpty() || current.isNullOrEmpty()) return this
    val filtered = current.mapNotNull { reaction ->
        val ids = reaction.userIds ?: return@mapNotNull reaction
        val remaining = ids.filterNot { it in blocked }
        when {
            remaining.size == ids.size -> reaction
            remaining.isEmpty() -> null
            else -> reaction.copy(userIds = remaining, count = remaining.size)
        }
    }
    return if (filtered == current) this else copy(reactions = filtered)
}
