package com.echon.voice.feature.friends

import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.apiCall
import com.echon.voice.model.FriendByNameRequest
import com.echon.voice.model.FriendRequest
import com.echon.voice.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Friends + incoming/outgoing requests, mirroring the iOS `FriendsStore`. */
@Singleton
class FriendsStore @Inject constructor(
    private val api: EchonApi,
) {
    private val _friends = MutableStateFlow<List<User>>(emptyList())
    val friends: StateFlow<List<User>> = _friends.asStateFlow()

    private val _incoming = MutableStateFlow<List<FriendRequest>>(emptyList())
    val incoming: StateFlow<List<FriendRequest>> = _incoming.asStateFlow()

    private val _outgoing = MutableStateFlow<List<FriendRequest>>(emptyList())
    val outgoing: StateFlow<List<FriendRequest>> = _outgoing.asStateFlow()

    suspend fun load() {
        _friends.value = apiCall { api.myFriends() }
        val requests = apiCall { api.friendRequests() }
        _incoming.value = requests.incoming
        _outgoing.value = requests.outgoing
    }

    /** Add by "name#1234"; discriminator optional. */
    suspend fun sendRequest(handle: String) {
        val parts = handle.trim().split("#", limit = 2)
        apiCall { api.sendFriendRequest(FriendByNameRequest(parts[0], parts.getOrNull(1))) }
        load()
    }

    suspend fun accept(requestId: String) {
        apiCall { api.acceptFriendRequest(requestId) }
        load()
    }

    suspend fun decline(requestId: String) {
        apiCall { api.deleteFriendRequest(requestId) }
        load()
    }

    suspend fun remove(userId: String) {
        apiCall { api.removeFriend(userId) }
        load()
    }
}
