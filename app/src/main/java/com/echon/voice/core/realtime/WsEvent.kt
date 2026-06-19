package com.echon.voice.core.realtime

import com.echon.voice.core.network.EchonJson
import com.echon.voice.model.Message
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One frame from wss://echon-voice.com/v1/ws, plus synthetic lifecycle events.
 * Envelope: {event|type, data, actor_id, ts}; `ready` uses top-level `type`.
 * Unrecognized events decode to [Unknown] so new server features never crash
 * the client — the tolerant-enum discipline carried over from iOS.
 */
sealed interface WsEvent {
    data object Ready : WsEvent
    data class MessageNew(val message: Message) : WsEvent
    data class MessageUpdated(val message: Message) : WsEvent
    data class MessageDeleted(val messageId: String, val channelId: String?) : WsEvent
    data class TypingUpdate(val channelId: String, val userId: String, val isTyping: Boolean) : WsEvent
    data class PresenceChanged(val userId: String, val status: String) : WsEvent
    data class ReadStateUpdated(val channelId: String?) : WsEvent
    data class UserBlocked(val userId: String) : WsEvent
    data class UserUnblocked(val userId: String) : WsEvent
    data object VoiceStateChanged : WsEvent
    data object FriendsChanged : WsEvent
    data class Unknown(val name: String) : WsEvent

    // Synthetic lifecycle (from WsClient, not server frames).
    data object SocketConnected : WsEvent
    data object SocketDisconnected : WsEvent
}

@Serializable
private data class DeletedPayload(val messageId: String? = null, val id: String? = null, val channelId: String? = null)

@Serializable
private data class TypingPayload(val channelId: String? = null, val userId: String? = null, val typing: Boolean? = null)

@Serializable
private data class PresencePayload(val userId: String? = null, val status: String? = null)

@Serializable
private data class BlockPayload(val userId: String? = null)

@Serializable
private data class ReadStatePayload(val channelId: String? = null)

object WsEventParser {
    fun parse(text: String): WsEvent? {
        val root = runCatching { EchonJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val name = (root["event"] ?: root["type"])?.jsonPrimitive?.content ?: return null
        val data: JsonElement? = root["data"]

        fun <T> payload(serializer: KSerializer<T>): T? =
            data?.let { runCatching { EchonJson.decodeFromJsonElement(serializer, it) }.getOrNull() }

        return when (name) {
            "ready" -> WsEvent.Ready
            "message-new", "message.created" ->
                payload(Message.serializer())?.let { WsEvent.MessageNew(it) } ?: WsEvent.Unknown(name)
            "message-updated", "message.updated" ->
                payload(Message.serializer())?.let { WsEvent.MessageUpdated(it) } ?: WsEvent.Unknown(name)
            "message-deleted", "message.deleted" -> {
                val body = payload(DeletedPayload.serializer())
                val id = body?.messageId ?: body?.id
                if (id != null) WsEvent.MessageDeleted(id, body?.channelId) else WsEvent.Unknown(name)
            }
            "typing-update" -> {
                val body = payload(TypingPayload.serializer())
                if (body?.channelId != null && body.userId != null) {
                    WsEvent.TypingUpdate(body.channelId, body.userId, body.typing ?: false)
                } else WsEvent.Unknown(name)
            }
            "presence-changed", "presence.update" -> {
                val body = payload(PresencePayload.serializer())
                if (body?.userId != null) WsEvent.PresenceChanged(body.userId, body.status ?: "offline")
                else WsEvent.Unknown(name)
            }
            "read-state-updated" -> WsEvent.ReadStateUpdated(payload(ReadStatePayload.serializer())?.channelId)
            "user-blocked" ->
                payload(BlockPayload.serializer())?.userId?.let { WsEvent.UserBlocked(it) } ?: WsEvent.Unknown(name)
            "user-unblocked" ->
                payload(BlockPayload.serializer())?.userId?.let { WsEvent.UserUnblocked(it) } ?: WsEvent.Unknown(name)
            "voice-joined", "voice-left", "voice-state-changed", "voice-moved" -> WsEvent.VoiceStateChanged
            "friend-added", "friend-removed", "friend-request-incoming", "friend-request-outgoing-cancelled" ->
                WsEvent.FriendsChanged
            else -> WsEvent.Unknown(name)
        }
    }
}
