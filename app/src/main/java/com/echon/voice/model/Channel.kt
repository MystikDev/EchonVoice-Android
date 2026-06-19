package com.echon.voice.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Server text channels and DM conversations share message/pin semantics under
 *  different API roots (/v1/channels/… vs /v1/dms/…). */
enum class ChatChannelKind { SERVER, DM }

@Serializable
enum class ChannelKind {
    @SerialName("text") TEXT,
    @SerialName("voice") VOICE,
    UNKNOWN, // coerced fallback for unrecognized server values
}

@Serializable
data class Channel(
    val id: String,
    val name: String? = null,
    val type: ChannelKind = ChannelKind.UNKNOWN,
    val position: Int? = null,
    val parentId: String? = null,
    val serverId: String? = null,
    val topic: String? = null,
    val readOnly: Boolean? = null,
    val createdAt: String? = null,
)

@Serializable
data class ChannelsResponse(val channels: List<Channel>)
