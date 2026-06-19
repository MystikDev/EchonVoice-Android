package com.echon.voice.feature.dms

import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.apiCall
import com.echon.voice.model.DMConversation
import com.echon.voice.model.OpenDmRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Direct-message conversations, mirroring the iOS `DMsStore`. */
@Singleton
class DMsStore @Inject constructor(
    private val api: EchonApi,
) {
    private val _conversations = MutableStateFlow<List<DMConversation>>(emptyList())
    val conversations: StateFlow<List<DMConversation>> = _conversations.asStateFlow()

    suspend fun load() {
        _conversations.value = apiCall { api.myDms() }.sortedByDescending { it.lastMessageAt ?: it.createdAt }
    }

    /** Opens (or returns the existing) DM with a recipient. */
    suspend fun open(recipientId: String): DMConversation {
        val conversation = apiCall { api.openDm(OpenDmRequest(recipientId)) }
        if (_conversations.value.none { it.id == conversation.id }) {
            _conversations.update { listOf(conversation) + it }
        }
        return conversation
    }

    /** Bump a conversation to the top when a new message arrives. */
    fun touch(conversationId: String, at: String?) {
        _conversations.update { list ->
            val idx = list.indexOfFirst { it.id == conversationId }
            if (idx < 0) return@update list
            val updated = list[idx].copy(lastMessageAt = at ?: list[idx].lastMessageAt)
            (listOf(updated) + list.filterIndexed { i, _ -> i != idx })
        }
    }
}
