package com.echon.voice.feature.servers

import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.apiCall
import com.echon.voice.model.Channel
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
    @Volatile private var generation = 0L
    fun clear() {
        generation++
        _servers.value = emptyList(); _channelsByServer.value = emptyMap(); _selectedServerId.value = null; _revokedChannelIds.value = emptySet()
    }

    private val _revokedChannelIds = MutableStateFlow<Set<String>>(emptySet())
    val revokedChannelIds = _revokedChannelIds.asStateFlow()

    fun upsertChannel(channel: Channel) {
        val serverId = channel.serverId ?: return
        generation++ // Fence REST snapshots started before this permission event.
        _revokedChannelIds.update { it - channel.id }
        _channelsByServer.update { map ->
            val channels = map[serverId].orEmpty()
            map + (serverId to (channels.filterNot { it.id == channel.id } + channel))
        }
    }

    fun removeChannel(channelId: String) {
        generation++
        _revokedChannelIds.update { it + channelId }
        _channelsByServer.update { map -> map.mapValues { (_, channels) -> channels.filterNot { it.id == channelId } } }
    }

    private val _servers = MutableStateFlow<List<Server>>(emptyList())
    val servers: StateFlow<List<Server>> = _servers.asStateFlow()

    private val _channelsByServer = MutableStateFlow<Map<String, List<Channel>>>(emptyMap())
    val channelsByServer: StateFlow<Map<String, List<Channel>>> = _channelsByServer.asStateFlow()

    private val _selectedServerId = MutableStateFlow<String?>(null)
    val selectedServerId: StateFlow<String?> = _selectedServerId.asStateFlow()

    suspend fun loadServers() {
        val epoch = generation
        val response = apiCall { api.myServers() }
        if (epoch != generation) return
        _servers.value = response.servers
        val ids = response.servers.map { it.id }.toSet()
        val removed = _channelsByServer.value.filterKeys { it !in ids }.values.flatten().map { it.id }.toSet()
        _revokedChannelIds.update { it + removed }
        _channelsByServer.update { map -> map.filterKeys { it in ids } }
        if (_selectedServerId.value !in ids) _selectedServerId.value = response.servers.firstOrNull()?.id
        _selectedServerId.value?.let { loadChannels(it) }
    }

    suspend fun loadChannels(serverId: String) {
        val epoch = generation
        val response = apiCall { api.serverChannels(serverId) }
        if (epoch == generation) {
            val current = response.channels.map { it.id }.toSet()
            val removed = _channelsByServer.value[serverId].orEmpty().map { it.id }.toSet() - current
            _revokedChannelIds.update { ids -> (ids + removed) - current }
            _channelsByServer.update { it + (serverId to response.channels) }
        }
    }

    suspend fun select(serverId: String) {
        _selectedServerId.value = serverId
        loadChannels(serverId)
    }

    fun channelsFor(serverId: String?): List<Channel> =
        (_channelsByServer.value[serverId] ?: emptyList()).sortedBy { it.position ?: 0 }
}
