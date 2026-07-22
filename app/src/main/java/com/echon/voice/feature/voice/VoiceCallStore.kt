package com.echon.voice.feature.voice

import android.content.Context
import com.echon.voice.core.di.ApplicationScope
import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.apiCall
import com.echon.voice.model.VoiceStateUpdateRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import io.livekit.android.LiveKit
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.LocalVideoTrackOptions
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
    val hasCamera: Boolean,
)

/**
 * A watchable video stream (camera or screen) from any participant. As on iOS,
 * cameras are treated exactly like screen shares: the avatar stays in the
 * roster and the stream renders in the stage area.
 */
data class LiveStream(
    val id: String,
    val title: String,
    val isScreen: Boolean,
    val track: VideoTrack,
)

/**
 * Owns the single active LiveKit voice call — the Android port of the iOS
 * `VoiceCallStore`. The web client publishes screen share as a second
 * `<identity>:screen` participant; phone cameras publish as a plain camera
 * track. Both surface in [liveStreams] for view rendering.
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

    private val _isCameraOn = MutableStateFlow(false)
    val isCameraOn: StateFlow<Boolean> = _isCameraOn.asStateFlow()

    private val _channelName = MutableStateFlow<String?>(null)
    val channelName: StateFlow<String?> = _channelName.asStateFlow()

    private val _liveStreams = MutableStateFlow<List<LiveStream>>(emptyList())
    val liveStreams: StateFlow<List<LiveStream>> = _liveStreams.asStateFlow()

    /** Mid-call publish/capture failures, for a transient in-call notice. */
    private val _publishError = MutableStateFlow<String?>(null)
    val publishError: StateFlow<String?> = _publishError.asStateFlow()
    fun clearPublishError() { _publishError.value = null }

    var room: Room? = null
        private set
    private var eventsJob: Job? = null
    private var activeChannelId: String? = null
    private var cameraPosition = CameraPosition.FRONT

    fun join(channelId: String, channelName: String) {
        if (_state.value != CallState.Idle) return
        _state.value = CallState.Connecting
        _channelName.value = channelName
        activeChannelId = channelId
        scope.launch {
            try {
                val grant = apiCall { api.joinVoice(channelId) }
                val room = LiveKit.create(context.applicationContext)
                // Front camera by default, mirroring iOS (CameraCaptureOptions position: .front).
                room.videoTrackCaptureDefaults = LocalVideoTrackOptions(position = CameraPosition.FRONT)
                this@VoiceCallStore.room = room
                eventsJob = scope.launch { room.events.collect { sync(room) } }
                room.connect(grant.livekitUrl, grant.token)
                room.localParticipant.setMicrophoneEnabled(true)
                _isMuted.value = false
                CallForegroundService.start(context)
                _state.value = CallState.Connected
                sync(room)
                reportVoiceState()
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
            reportVoiceState()
        }
    }

    /** Start/stop streaming the phone camera into the call. Needs the CAMERA runtime grant. */
    fun toggleCamera() {
        val room = room ?: return
        if (_state.value != CallState.Connected) return
        scope.launch {
            val enable = !_isCameraOn.value
            try {
                val local = room.localParticipant
                if (enable) {
                    local.setCameraEnabled(true)
                    _isCameraOn.value = local.getTrackPublication(Track.Source.CAMERA)?.track != null
                } else {
                    // setCameraEnabled(false) only MUTES — the publication lingers
                    // and other clients keep a frozen tile (same gotcha as iOS).
                    // Fully unpublish so the stream vanishes everywhere.
                    local.getTrackPublication(Track.Source.CAMERA)?.track
                        ?.let { local.unpublishTrack(it) }
                    _isCameraOn.value = false
                }
                sync(room)
                reportVoiceState()
            } catch (e: Exception) {
                _publishError.value = "Couldn't ${if (enable) "start" else "stop"} the camera."
            }
        }
    }

    /** Switch between front and back cameras while streaming. */
    fun flipCamera() {
        val local = room?.localParticipant ?: return
        val track = local.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack ?: return
        scope.launch {
            try {
                // Pass the target explicitly — a bare switchCamera() (both args
                // null) is a no-op in the SDK.
                val target = if (cameraPosition == CameraPosition.FRONT) CameraPosition.BACK else CameraPosition.FRONT
                track.switchCamera(position = target)
                cameraPosition = target
            } catch (e: Exception) {
                _publishError.value = "Couldn't switch cameras."
            }
        }
    }

    fun leave() {
        scope.launch {
            activeChannelId?.let { id -> runCatching { apiCall { api.leaveVoice(id) } } }
            cleanup()
        }
    }

    /**
     * Best-effort report of mic/camera/screen state so server occupancy and
     * every other client's roster reflect it (LiveKit only carries the media).
     */
    private suspend fun reportVoiceState() {
        runCatching {
            apiCall {
                api.updateVoiceState(
                    VoiceStateUpdateRequest(
                        muted = _isMuted.value,
                        video = _isCameraOn.value,
                        screen = false, // phones never publish a screen
                    ),
                )
            }
        }
    }

    private fun cleanup() {
        eventsJob?.cancel()
        eventsJob = null
        room?.disconnect()
        room = null
        activeChannelId = null
        _participants.value = emptyList()
        _liveStreams.value = emptyList()
        _isCameraOn.value = false
        cameraPosition = CameraPosition.FRONT
        _publishError.value = null
        _channelName.value = null
        _state.value = CallState.Idle
        CallForegroundService.stop(context)
    }

    private fun sync(room: Room) {
        val local = room.localParticipant

        // Self-heal downward: if the camera track ended outside our control
        // (e.g. the system revoked capture), reflect that instead of a stuck icon.
        if (_isCameraOn.value && local.getTrackPublication(Track.Source.CAMERA)?.track == null) {
            _isCameraOn.value = false
        }

        val all = buildList {
            add(toCallParticipant(local, isLocal = true))
            room.remoteParticipants.values.forEach { remote ->
                // The web client's screen share joins as a separate "<identity>:screen"
                // participant — that's a stream, not a person; it renders via liveStreams.
                val identity = remote.identity?.value ?: ""
                if (!identity.endsWith(":screen")) add(toCallParticipant(remote, isLocal = false))
            }
        }
        _participants.value = all
        _liveStreams.value = collectLiveStreams(room)
    }

    /** Every active camera/screen stream — local first, then remotes by identity. */
    private fun collectLiveStreams(room: Room): List<LiveStream> = buildList {
        val local = room.localParticipant
        if (_isCameraOn.value) {
            (local.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack)?.let {
                add(LiveStream(id = "local-camera", title = "You", isScreen = false, track = it))
            }
        }

        room.remoteParticipants.values
            .sortedBy { it.identity?.value ?: "" }
            .forEach { remote ->
                val identity = remote.identity?.value ?: ""
                val baseName = remote.name?.takeIf { it.isNotBlank() }
                    ?: identity.removeSuffix(":screen").take(8)
                remote.trackPublications.values.forEach { pub ->
                    val track = pub.track as? VideoTrack ?: return@forEach
                    if (pub.muted) return@forEach // a muted camera is a frozen frame, not a stream
                    val isScreen = pub.source == Track.Source.SCREEN_SHARE || identity.endsWith(":screen")
                    val isCamera = pub.source == Track.Source.CAMERA
                    if (isScreen || isCamera) {
                        add(LiveStream(id = "$identity-${pub.sid}", title = baseName, isScreen = isScreen, track = track))
                    }
                }
            }
    }

    private fun toCallParticipant(participant: Participant, isLocal: Boolean): CallParticipant {
        val identity = participant.identity?.value ?: ""
        val isScreen = identity.endsWith(":screen") ||
            participant.trackPublications.values.any { it.source == Track.Source.SCREEN_SHARE }
        val hasCamera = if (isLocal) {
            _isCameraOn.value
        } else {
            participant.trackPublications.values.any {
                it.source == Track.Source.CAMERA && it.track != null && !it.muted
            }
        }
        val micMuted = participant.trackPublications.values
            .firstOrNull { it.source == Track.Source.MICROPHONE }?.muted ?: isLocal.let { _isMuted.value && isLocal }
        return CallParticipant(
            id = identity,
            name = participant.name?.takeIf { it.isNotBlank() } ?: identity,
            isLocal = isLocal,
            isMuted = if (isLocal) _isMuted.value else micMuted,
            isSpeaking = participant.isSpeaking,
            isScreenSharer = isScreen,
            hasCamera = hasCamera,
        )
    }
}
