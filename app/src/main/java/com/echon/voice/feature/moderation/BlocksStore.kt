package com.echon.voice.feature.moderation

import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.apiCall
import com.echon.voice.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for blocked users — the Android analog of the iOS
 * `BlocksStore`. [blockedIds] is the O(1) set that the render-time block filter
 * consults everywhere, so blocking removes a user's content instantly across the
 * whole app *by construction* (the make-or-break store-review behavior).
 *
 * Block/unblock are optimistic: the UI updates before the server confirms, and
 * rolls back on failure.
 */
@Singleton
class BlocksStore @Inject constructor(
    private val api: EchonApi,
) {
    private val _blockedIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedIds: StateFlow<Set<String>> = _blockedIds.asStateFlow()

    private val _blockedUsers = MutableStateFlow<List<User>>(emptyList())
    val blockedUsers: StateFlow<List<User>> = _blockedUsers.asStateFlow()

    fun isBlocked(userId: String?): Boolean =
        userId != null && _blockedIds.value.contains(userId)

    suspend fun load() {
        val users = apiCall { api.myBlocks() }
        _blockedUsers.value = users
        _blockedIds.value = users.map { it.id }.toSet()
    }

    /** Optimistic: the user's content vanishes the moment block is confirmed in-app. */
    suspend fun block(user: User) {
        _blockedIds.update { it + user.id }
        if (_blockedUsers.value.none { it.id == user.id }) {
            _blockedUsers.update { it + user }
        }
        try {
            apiCall { api.block(user.id) }
        } catch (e: Exception) {
            _blockedIds.update { it - user.id }
            _blockedUsers.update { list -> list.filterNot { it.id == user.id } }
            throw e
        }
    }

    suspend fun unblock(userId: String) {
        val previousIds = _blockedIds.value
        val previousUsers = _blockedUsers.value
        _blockedIds.update { it - userId }
        _blockedUsers.update { list -> list.filterNot { it.id == userId } }
        try {
            apiCall { api.unblock(userId) }
        } catch (e: Exception) {
            _blockedIds.value = previousIds
            _blockedUsers.value = previousUsers
            throw e
        }
    }

    /** Apply a block/unblock that happened on another device (WS user-blocked frame). */
    fun applyRemote(userId: String, blocked: Boolean) {
        if (blocked) {
            _blockedIds.update { it + userId }
        } else {
            _blockedIds.update { it - userId }
            _blockedUsers.update { list -> list.filterNot { it.id == userId } }
        }
    }
}
