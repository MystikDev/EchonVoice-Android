package com.echon.voice.model

import kotlinx.serialization.Serializable

/** One participant in a voice channel (GET /v1/servers/{id}/voice). */
@Serializable
data class VoiceParticipantState(
    val user: User? = null,
    val muted: Boolean? = null,
    val deafened: Boolean? = null,
    val video: Boolean? = null,
    val screen: Boolean? = null,
    val joinedAt: String? = null,
)

/** POST /v1/channels/{id}/voice/join → LiveKit credentials. */
@Serializable
data class VoiceJoinResponse(
    val token: String,
    val livekitUrl: String,
    val room: String? = null,
)
