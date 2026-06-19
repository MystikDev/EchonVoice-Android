package com.echon.voice.model

import kotlinx.serialization.Serializable

/** GET /v1/me/dms → bare array. Its `id` doubles as the channel id (channel_kind "dm"). */
@Serializable
data class DMConversation(
    val id: String,
    val isGroup: Boolean? = null,
    val groupName: String? = null,
    val participants: List<User>? = null,
    val lastMessageAt: String? = null,
    val createdAt: String? = null,
) {
    fun other(myId: String?): User? = participants?.firstOrNull { it.id != myId } ?: participants?.firstOrNull()

    fun displayName(myId: String?): String {
        if (isGroup == true && !groupName.isNullOrEmpty()) return groupName
        return other(myId)?.username ?: "Unknown"
    }
}

@Serializable
data class OpenDmRequest(val recipientId: String)

/** GET /v1/me/friends/requests → {incoming, outgoing}. */
@Serializable
data class FriendRequest(
    val id: String,
    val sender: User? = null,
    val receiver: User? = null,
    val createdAt: String? = null,
)

@Serializable
data class FriendRequestsResponse(
    val incoming: List<FriendRequest> = emptyList(),
    val outgoing: List<FriendRequest> = emptyList(),
)

/** Add a friend by "name#1234" — discriminator optional. */
@Serializable
data class FriendByNameRequest(val username: String, val discriminator: String? = null)

/** POST /v1/channels/{id}/invites response. */
@Serializable
data class Invite(
    val code: String,
    val serverId: String? = null,
    val channelId: String? = null,
    val uses: Int? = null,
    val maxUses: Int? = null,
    val expiresAt: String? = null,
)

/** GET /v1/invites/{code} — enough to render a join card. */
@Serializable
data class InvitePreview(
    val code: String,
    val server: ServerSummary? = null,
    val channel: ChannelSummary? = null,
    val creator: User? = null,
    val expiresAt: String? = null,
) {
    @Serializable
    data class ServerSummary(val id: String, val name: String? = null, val icon: String? = null, val memberCount: Int? = null)

    @Serializable
    data class ChannelSummary(val id: String, val name: String? = null)
}

/** GET /v1/servers/{id}/members → {members: [{user, nickname, ...}]}. */
@Serializable
data class Member(
    val user: User,
    val nickname: String? = null,
    val serverAvatar: String? = null,
    val joinedAt: String? = null,
) {
    val displayName: String get() = nickname ?: user.username ?: "Unknown"
}

@Serializable
data class MembersResponse(val members: List<Member>)
