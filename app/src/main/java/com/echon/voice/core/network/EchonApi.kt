package com.echon.voice.core.network

import com.echon.voice.model.AppRelease
import com.echon.voice.model.ChannelsResponse
import com.echon.voice.model.DMConversation
import com.echon.voice.model.DeleteAccountRequest
import com.echon.voice.model.FriendByNameRequest
import com.echon.voice.model.FriendRequestsResponse
import com.echon.voice.model.Invite
import com.echon.voice.model.InvitePreview
import com.echon.voice.model.MembersResponse
import com.echon.voice.model.OpenDmRequest
import com.echon.voice.model.EditMessageRequest
import com.echon.voice.model.LoginRequest
import com.echon.voice.model.LoginResponse
import com.echon.voice.model.MeResponse
import com.echon.voice.model.Message
import com.echon.voice.model.MessagesResponse
import com.echon.voice.model.RegisterRequest
import com.echon.voice.model.ReportRequest
import com.echon.voice.model.SendMessageRequest
import com.echon.voice.model.ServersResponse
import com.echon.voice.model.TicketResponse
import com.echon.voice.model.UploadResponse
import com.echon.voice.model.User
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Retrofit surface for the echon-voice.com /v1 API. Grows per phase; Phase 0/1
 * covers auth + session. The iOS `Endpoint` enum is the contract reference.
 */
interface EchonApi {

    @POST("v1/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): LoginResponse

    @POST("v1/auth/logout")
    suspend fun logout()

    @GET("v1/me")
    suspend fun me(): MeResponse

    @POST("v1/me/accept-tos")
    suspend fun acceptTos()

    // --- Moderation (Play UGC policy / store review) ---

    @POST("v1/messages/{id}/report")
    suspend fun reportMessage(@Path("id") id: String, @Body body: ReportRequest)

    @POST("v1/users/{id}/report")
    suspend fun reportUser(@Path("id") id: String, @Body body: ReportRequest)

    /** GET → bare array of blocked users. */
    @GET("v1/me/blocks")
    suspend fun myBlocks(): List<User>

    @POST("v1/me/blocks/{id}")
    suspend fun block(@Path("id") userId: String)

    @DELETE("v1/me/blocks/{id}")
    suspend fun unblock(@Path("id") userId: String)

    @POST("v1/me/delete")
    suspend fun deleteAccount(@Body body: DeleteAccountRequest)

    @PATCH("v1/me/profile")
    suspend fun updateProfile(@Body body: com.echon.voice.model.ProfileUpdateRequest): User

    @POST("v1/me/password")
    suspend fun changePassword(@Body body: com.echon.voice.model.ChangePasswordRequest)

    // --- Servers & channels ---

    @GET("v1/me/servers")
    suspend fun myServers(): ServersResponse

    @GET("v1/servers/{id}/channels")
    suspend fun serverChannels(@Path("id") serverId: String): ChannelsResponse

    // --- Messages (server channels) ---

    @GET("v1/channels/{id}/messages")
    suspend fun channelMessages(
        @Path("id") channelId: String,
        @Query("before") before: String? = null,
        @Query("limit") limit: Int = 50,
    ): MessagesResponse

    @POST("v1/channels/{id}/messages")
    suspend fun sendChannelMessage(@Path("id") channelId: String, @Body body: SendMessageRequest): Message

    @GET("v1/channels/{id}/pins")
    suspend fun channelPins(@Path("id") channelId: String): MessagesResponse

    // --- Messages (DMs) ---

    @GET("v1/dms/{id}/messages")
    suspend fun dmMessages(
        @Path("id") channelId: String,
        @Query("before") before: String? = null,
        @Query("limit") limit: Int = 50,
    ): MessagesResponse

    @POST("v1/dms/{id}/messages")
    suspend fun sendDmMessage(@Path("id") channelId: String, @Body body: SendMessageRequest): Message

    @GET("v1/dms/{id}/pins")
    suspend fun dmPins(@Path("id") channelId: String): MessagesResponse

    // --- Message mutations ---

    @PATCH("v1/messages/{id}")
    suspend fun editMessage(@Path("id") id: String, @Body body: EditMessageRequest): Message

    @DELETE("v1/messages/{id}")
    suspend fun deleteMessage(@Path("id") id: String)

    @PUT("v1/messages/{id}/reactions/{emoji}")
    suspend fun addReaction(@Path("id") id: String, @Path("emoji") emoji: String)

    @DELETE("v1/messages/{id}/reactions/{emoji}/me")
    suspend fun removeReaction(@Path("id") id: String, @Path("emoji") emoji: String)

    @POST("v1/messages/{id}/pin")
    suspend fun pinMessage(@Path("id") id: String)

    @DELETE("v1/messages/{id}/pin")
    suspend fun unpinMessage(@Path("id") id: String)

    // --- Uploads & realtime ---

    @Multipart
    @POST("v1/uploads")
    suspend fun upload(
        @Part file: MultipartBody.Part,
        @Part("purpose") purpose: RequestBody,
    ): UploadResponse

    @POST("v1/ws/ticket")
    suspend fun wsTicket(): TicketResponse

    // --- DMs ---

    @GET("v1/me/dms")
    suspend fun myDms(): List<DMConversation>

    @POST("v1/me/dms")
    suspend fun openDm(@Body body: OpenDmRequest): DMConversation

    // --- Friends ---

    @GET("v1/me/friends")
    suspend fun myFriends(): List<User>

    @GET("v1/me/friends/requests")
    suspend fun friendRequests(): FriendRequestsResponse

    @POST("v1/me/friends/requests")
    suspend fun sendFriendRequest(@Body body: FriendByNameRequest)

    @PUT("v1/me/friends/requests/{id}/accept")
    suspend fun acceptFriendRequest(@Path("id") id: String)

    @DELETE("v1/me/friends/requests/{id}")
    suspend fun deleteFriendRequest(@Path("id") id: String)

    @DELETE("v1/me/friends/{id}")
    suspend fun removeFriend(@Path("id") userId: String)

    // --- Invites ---

    @POST("v1/channels/{id}/invites")
    suspend fun createInvite(@Path("id") channelId: String): Invite

    @GET("v1/invites/{code}")
    suspend fun previewInvite(@Path("code") code: String): InvitePreview

    @POST("v1/invites/{code}/use")
    suspend fun useInvite(@Path("code") code: String)

    // --- Members ---

    @GET("v1/servers/{id}/members")
    suspend fun serverMembers(@Path("id") serverId: String): MembersResponse

    // --- Voice ---

    @GET("v1/servers/{id}/voice")
    suspend fun serverVoice(@Path("id") serverId: String): Map<String, List<com.echon.voice.model.VoiceParticipantState>>

    @POST("v1/channels/{id}/voice/join")
    suspend fun joinVoice(@Path("id") channelId: String): com.echon.voice.model.VoiceJoinResponse

    @POST("v1/channels/{id}/voice/leave")
    suspend fun leaveVoice(@Path("id") channelId: String)
}
