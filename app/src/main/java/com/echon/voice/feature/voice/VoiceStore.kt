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
    servers: ServersStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** channelId → participants currently in that voice channel. */
    private val _occupancy = MutableStateFlow<Map<String, List<VoiceParticipantState>>>(emptyMap())
    val occupancy: StateFlow<Map<String, List<VoiceParticipantState>>> = _occupancy.asStateFlow()

    @Volatile private var loadedServerId: String? = null

    init {
        scope.launch {
            servers.selectedServerId.collectLatest { id ->
                if (id == null) {
                    _occupancy.value = emptyMap()
                } else {
                    load(id)
                }
            }
        }
    }

    private suspend fun load(serverId: String) {
        loadedServerId = serverId
        runCatching { apiCall { api.serverVoice(serverId) } }
            .onSuccess { if (loadedServerId == serverId) _occupancy.value = it }
            .onFailure { if (loadedServerId == serverId) _occupancy.value = emptyMap() }
    }

    /** Refetch occupancy for the loaded server (called on voice-* WS events). */
    fun refresh() {
        loadedServerId?.let { id -> scope.launch { load(id) } }
    }
}
