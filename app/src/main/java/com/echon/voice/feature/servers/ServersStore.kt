package com.echon.voice.feature.servers

import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.apiCall
import com.echon.voice.model.Channel
import com.echon.voice.model.Member
import com.echon.voice.model.Server
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Servers + per-server channels, mirroring the iOS `ServersStore`. */
@Singleton
class ServersStore @Inject constructor(
    private val api: EchonApi,
) {
    private val _servers = MutableStateFlow<List<Server>>(emptyList())
    val servers: StateFlow<List<Server>> = _servers.asStateFlow()

    private val _channelsByServer = MutableStateFlow<Map<String, List<Channel>>>(emptyMap())
    val channelsByServer: StateFlow<Map<String, List<Channel>>> = _channelsByServer.asStateFlow()

    private val _selectedServerId = MutableStateFlow<String?>(null)
    val selectedServerId: StateFlow<String?> = _selectedServerId.asStateFlow()

    private val _membersByServer = MutableStateFlow<Map<String, List<Member>>>(emptyMap())
    val membersByServer: StateFlow<Map<String, List<Member>>> = _membersByServer.asStateFlow()

    suspend fun loadServers() {
        val response = apiCall { api.myServers() }
        _servers.value = response.servers
        if (_selectedServerId.value == null) _selectedServerId.value = response.servers.firstOrNull()?.id
        _selectedServerId.value?.let {
            loadChannels(it)
            loadMembers(it)
        }
    }

    suspend fun loadChannels(serverId: String) {
        val response = apiCall { api.serverChannels(serverId) }
        _channelsByServer.update { it + (serverId to response.channels) }
    }

    suspend fun loadMembers(serverId: String) {
        val response = apiCall { api.serverMembers(serverId) }
        _membersByServer.update { it + (serverId to response.members) }
    }

    suspend fun select(serverId: String) {
        _selectedServerId.value = serverId
        if (_channelsByServer.value[serverId] == null) loadChannels(serverId)
        if (_membersByServer.value[serverId] == null) loadMembers(serverId)
    }

    fun channelsFor(serverId: String?): List<Channel> =
        (_channelsByServer.value[serverId] ?: emptyList()).sortedBy { it.position ?: 0 }
}
