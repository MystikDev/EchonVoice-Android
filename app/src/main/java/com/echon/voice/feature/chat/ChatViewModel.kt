package com.echon.voice.feature.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.apiCall
import com.echon.voice.core.realtime.RealtimeStore
import com.echon.voice.feature.auth.AuthStore
import com.echon.voice.feature.moderation.BlocksStore
import com.echon.voice.model.ChatChannelKind
import com.echon.voice.model.Message
import com.echon.voice.model.OutgoingAttachment
import com.echon.voice.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val channelId: String = checkNotNull(savedState["channelId"])
    val channelName: String = savedState["channelName"] ?: "channel"
    private val kind = ChatChannelKind.SERVER

    private val store: MessageStore = chatStores.store(channelId, kind)
    val currentUserId: String? get() = auth.currentUser.value?.id

    /** Messages rendered through the block filter — blocking removes content instantly. */
    val visibleMessages: StateFlow<List<Message>> =
        combine(store.messages, blocks.blockedIds) { messages, blocked ->
            messages.filter { it.author?.id !in blocked }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val loadingOlder: StateFlow<Boolean> = store.loadingOlder

    val typingNames: StateFlow<List<String>> =
        realtime.typingUsers.map { map ->
            (map[channelId] ?: emptySet()).map { id ->
                store.messages.value.lastOrNull { it.author?.id == id }?.author?.username ?: "Someone"
            }.sorted()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var draft by mutableStateOf("")
    var replyTarget by mutableStateOf<Message?>(null)
        private set
    var editTarget by mutableStateOf<Message?>(null)
        private set
    var pending by mutableStateOf<List<OutgoingAttachment>>(emptyList())
        private set
    var uploading by mutableStateOf(false)
        private set

    fun onEnter() {
        realtime.channelOpened(channelId)
        viewModelScope.launch { store.loadInitial() }
    }

    fun onLeave() = realtime.channelClosed(channelId)

    fun onDraftChange(text: String) {
        draft = text
        if (text.isNotEmpty()) realtime.userIsTyping(channelId)
    }

    fun loadOlder() = viewModelScope.launch { store.loadOlder() }

    fun send() {
        val edit = editTarget
        if (edit != null) {
            val text = draft
            viewModelScope.launch { store.edit(edit.id, text) }
            clearContext()
            draft = ""
            return
        }
        if (draft.isBlank() && pending.isEmpty()) return
        val text = draft
        val reply = replyTarget?.id
        val attachments = pending.takeIf { it.isNotEmpty() }
        draft = ""
        replyTarget = null
        pending = emptyList()
        viewModelScope.launch { store.send(text, auth.currentUser.value, reply, attachments) }
    }

    fun startReply(message: Message) { editTarget = null; replyTarget = message }
    fun startEdit(message: Message) { replyTarget = null; editTarget = message; draft = message.content.orEmpty() }
    fun clearContext() {
        replyTarget = null
        if (editTarget != null) draft = ""
        editTarget = null
    }

    fun react(message: Message, emoji: String) =
        viewModelScope.launch { store.toggleReaction(message.id, emoji, currentUserId) }

    fun togglePin(message: Message) =
        viewModelScope.launch { store.setPinned(message.id, message.isPinned != true) }

    fun delete(message: Message) = viewModelScope.launch { store.delete(message.id) }
    fun retry(message: Message) = viewModelScope.launch { store.retry(message.id, auth.currentUser.value) }
    fun discard(message: Message) = store.discard(message.id)

    suspend fun block(user: User) = blocks.block(user)

    fun repliedTo(message: Message): Message? = store.message(withId = message.replyToId)

    fun isMine(message: Message): Boolean = message.author?.id == currentUserId

    fun attach(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            uploading = true
            try {
                val resolver = context.contentResolver
                val results = withContext(Dispatchers.IO) {
                    uris.take(10 - pending.size).mapNotNull { uri ->
                        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@mapNotNull null
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
                pending = pending + results
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
}
