package com.echon.voice.feature.voice

import com.echon.voice.core.di.ApplicationScope
import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.apiCall
import com.echon.voice.feature.servers.ServersStore
import com.echon.voice.model.VoiceParticipantState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live voice-channel occupancy for the selected server — who is currently in
 * which voice channel (GET /v1/servers/{id}/voice → {channelId: [participant…]}).
 * Reloaded when the selected server changes and on any voice-* websocket event
 * (the iOS `VoiceStore` does the same "refetch on voice event").
 */
@Singleton
class VoiceStore @Inject constructor(
    private val api: EchonApi,
    private val servers: ServersStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** channelId → participants currently in that voice channel. */
    private val _occupancy = MutableStateFlow<Map<String, List<VoiceParticipantState>>>(emptyMap())
    val occupancy: StateFlow<Map<String, List<VoiceParticipantState>>> = _occupancy.asStateFlow()

    private var revision = 0L

    fun removeChannel(channelId: String) {
        revision++
        _occupancy.value = _occupancy.value - channelId
    }

    @Volatile private var loadedServerId: String? = null

    init {
        scope.launch {
            servers.selectedServerId.collectLatest { id ->
                if (id == null) {
                    loadedServerId = null
                    _occupancy.value = emptyMap()
                } else {
                    load(id)
                }
            }
        }
    }

    private suspend fun load(serverId: String) {
        loadedServerId = serverId
        val requestRevision = ++revision
        runCatching { apiCall { api.serverVoice(serverId) } }
            .onSuccess { if (loadedServerId == serverId && revision == requestRevision) _occupancy.value = it.filterKeys { id -> id !in servers.revokedChannelIds.value } }
            .onFailure { if (loadedServerId == serverId && revision == requestRevision) _occupancy.value = emptyMap() }
    }

    /** Refetch occupancy for the loaded server (called on voice-* WS events). */
    fun refresh() {
        loadedServerId?.let { id -> scope.launch { load(id) } }
    }
}
