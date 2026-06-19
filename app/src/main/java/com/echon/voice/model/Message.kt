package com.echon.voice.model

import com.echon.voice.core.network.ApiConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Message(
    val id: String,
    val channelId: String? = null,
    val channelKind: String? = null,
    val author: User? = null,
    val content: String? = null,
    val replyToId: String? = null,
    val threadId: String? = null,
    val editedAt: String? = null,
    val isPinned: Boolean? = null,
    val isSystem: Boolean? = null,
    val mentionedUserIds: List<String>? = null,
    val attachments: List<Attachment>? = null,
    val reactions: List<Reaction>? = null,
    val createdAt: String? = null,
    // Local-echo state for optimistic sends; never encoded/decoded.
    @Transient val isLocalEcho: Boolean = false,
    @Transient val sendFailed: Boolean = false,
)

@Serializable
data class Attachment(
    val id: String,
    val filename: String? = null,
    val mimetype: String? = null,
    val size: Int? = null,
    val url: String? = null,
) {
    val isImage: Boolean get() = mimetype?.startsWith("image/") == true

    val resolvedUrl: String?
        get() {
            val u = url?.takeIf { it.isNotEmpty() } ?: return null
            return if (u.startsWith("http")) u else ApiConfig.BASE_URL.trimEnd('/') + "/" + u.trimStart('/')
        }
}

/** Live shape: {count, emoji_key, user_ids}. */
@Serializable
data class Reaction(
    val emojiKey: String? = null,
    val count: Int? = null,
    val userIds: List<String>? = null,
) {
    fun includes(userId: String?): Boolean = userId != null && userIds?.contains(userId) == true
}

@Serializable
data class MessagesResponse(val messages: List<Message>)

/** POST body for sending a message. */
@Serializable
data class SendMessageRequest(
    val content: String,
    val replyToId: String? = null,
    val attachments: List<OutgoingAttachment>? = null,
)

@Serializable
data class OutgoingAttachment(
    val filename: String,
    val url: String,
    val mimetype: String,
    val size: Int,
)

/** POST /v1/uploads response. */
@Serializable
data class UploadResponse(
    val url: String,
    val mimetype: String? = null,
    val size: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
)

/** POST /v1/ws/ticket → {ticket}. */
@Serializable
data class TicketResponse(val ticket: String)

@Serializable
data class EditMessageRequest(val content: String)
