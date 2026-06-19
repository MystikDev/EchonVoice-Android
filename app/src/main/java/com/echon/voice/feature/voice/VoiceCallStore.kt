package com.echon.voice.feature.voice

import android.content.Context
import com.echon.voice.core.di.ApplicationScope
import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.apiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import io.livekit.android.LiveKit
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class CallParticipant(
    val id: String,
    val name: String,
    val isLocal: Boolean,
    val isMuted: Boolean,
    val isSpeaking: Boolean,
    val isScreenSharer: Boolean,
)

/**
 * Owns the single active LiveKit voice call — the Android port of the iOS
 * `VoiceCallStore`. The web client publishes screen share as a second
 * `<identity>:screen` participant; [screenShareTrack] surfaces it for view-only
 * rendering.
 */
@Singleton
class VoiceCallStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: EchonApi,
    @ApplicationScope private val scope: CoroutineScope,
) {
    enum class CallState { Idle, Connecting, Connected }

    private val _state = MutableStateFlow(CallState.Idle)
    val state: StateFlow<CallState> = _state.asStateFlow()

    private val _participants = MutableStateFlow<List<CallParticipant>>(emptyList())
    val participants: StateFlow<List<CallParticipant>> = _participants.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _channelName = MutableStateFlow<String?>(null)
    val channelName: StateFlow<String?> = _channelName.asStateFlow()

    private val _screenShareTrack = MutableStateFlow<VideoTrack?>(null)
    val screenShareTrack: StateFlow<VideoTrack?> = _screenShareTrack.asStateFlow()

    var room: Room? = null
        private set
    private var eventsJob: Job? = null
    private var activeChannelId: String? = null

    fun join(channelId: String, channelName: String) {
        if (_state.value != CallState.Idle) return
        _state.value = CallState.Connecting
        _channelName.value = channelName
        activeChannelId = channelId
        scope.launch {
            try {
                val grant = apiCall { api.joinVoice(channelId) }
                val room = LiveKit.create(context.applicationContext)
                this@VoiceCallStore.room = room
                eventsJob = scope.launch { room.events.collect { sync(room) } }
                room.connect(grant.livekitUrl, grant.token)
                room.localParticipant.setMicrophoneEnabled(true)
                _isMuted.value = false
                CallForegroundService.start(context)
                _state.value = CallState.Connected
                sync(room)
            } catch (e: Exception) {
                cleanup()
            }
        }
    }

    fun toggleMute() {
        val room = room ?: return
        scope.launch {
            val newMuted = !_isMuted.value
            room.localParticipant.setMicrophoneEnabled(!newMuted)
            _isMuted.value = newMuted
            sync(room)
        }
    }

    fun leave() {
        scope.launch {
            activeChannelId?.let { id -> runCatching { apiCall { api.leaveVoice(id) } } }
            cleanup()
        }
    }

    private fun cleanup() {
        eventsJob?.cancel()
        eventsJob = null
        room?.disconnect()
        room = null
        activeChannelId = null
        _participants.value = emptyList()
        _screenShareTrack.value = null
        _channelName.value = null
        _state.value = CallState.Idle
        CallForegroundService.stop(context)
    }

    private fun sync(room: Room) {
        val all = buildList {
            add(toCallParticipant(room.localParticipant, isLocal = true))
            room.remoteParticipants.values.forEach { add(toCallParticipant(it, isLocal = false)) }
        }
        _participants.value = all

        // Find a screen-share video track (web client uses a ":screen" identity).
        val screen = room.remoteParticipants.values.firstNotNullOfOrNull { participant ->
            participant.trackPublications.values
                .firstOrNull { it.source == Track.Source.SCREEN_SHARE }
                ?.track as? VideoTrack
        }
        _screenShareTrack.value = screen
    }

    private fun toCallParticipant(participant: Participant, isLocal: Boolean): CallParticipant {
        val identity = participant.identity?.value ?: ""
        val isScreen = identity.endsWith(":screen") ||
            participant.trackPublications.values.any { it.source == Track.Source.SCREEN_SHARE }
        val micMuted = participant.trackPublications.values
            .firstOrNull { it.source == Track.Source.MICROPHONE }?.muted ?: isLocal.let { _isMuted.value && isLocal }
        return CallParticipant(
            id = identity,
            name = participant.name?.takeIf { it.isNotBlank() } ?: identity,
            isLocal = isLocal,
            isMuted = if (isLocal) _isMuted.value else micMuted,
            isSpeaking = participant.isSpeaking,
            isScreenSharer = isScreen,
        )
    }
}
