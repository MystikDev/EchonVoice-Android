package com.echon.voice.feature.voice

import com.twilio.audioswitch.AudioDevice
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import io.livekit.android.events.RoomEvent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.echon.voice.core.network.TlsPinning
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
    enum class CallState { Idle, Connecting, Connected, Reconnecting }

    private val _state = MutableStateFlow(CallState.Idle)
    val state: StateFlow<CallState> = _state.asStateFlow()

    private val _participants = MutableStateFlow<List<CallParticipant>>(emptyList())
    val participants: StateFlow<List<CallParticipant>> = _participants.asStateFlow()

    private val _audio = MutableStateFlow(CallAudioState())
    val audio: StateFlow<CallAudioState> = _audio.asStateFlow()
    private var audioRouting: CallAudioRouting? = null

    fun selectAudioDevice(device: AudioDevice?) {
        try { audioRouting?.select(device) }
        catch (_: Exception) { _publishError.value = "Couldn't change audio output. Try again." }
    }

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
    private var stateReportJob: Job? = null
    private var callJob: Job? = null
    private var callScope: CoroutineScope? = null
    private val controls = Mutex()
    var sessionGeneration: Long = 0
        private set
    private var cameraForeground = false

    fun setCameraForeground(visible: Boolean) {
        cameraForeground = visible
        if (!visible) stopCameraForBackground()
    }

    fun stopIfSession(generation: Long) {
        if (generation == sessionGeneration) cleanup()
    }
    private var activeChannelId: String? = null
    private var cameraPosition = CameraPosition.FRONT

    // All state transitions run on Main; network/media suspension never owns cleanup
    // for a later call. Cancelling a join must not resurrect capture after Leave.
    fun join(channelId: String, channelName: String) {
        if (_state.value != CallState.Idle) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _publishError.value = "Microphone permission is required to join voice."
            return
        }
        sessionGeneration++
        _state.value = CallState.Connecting
        _publishError.value = null
        _channelName.value = channelName
        activeChannelId = channelId
        val job = SupervisorJob(scope.coroutineContext[Job])
        callJob = job
        val sessionScope = CoroutineScope(scope.coroutineContext + job + Dispatchers.Main.immediate)
        callScope = sessionScope
        sessionScope.launch {
            try {
                // Establish the microphone FGS while the user is still foreground,
                // before network latency can move audio startup into the background.
                CallForegroundService.start(context, sessionGeneration)
                val grant = apiCall { api.joinVoice(channelId) }
                currentCoroutineContext().ensureActive()
                val signaling = grant.livekitUrl.replaceFirst("wss://", "https://").toHttpUrlOrNull()
                require(grant.livekitUrl.startsWith("wss://") && signaling != null &&
                    TlsPinning.isApiOrigin(signaling)) { "Untrusted voice endpoint" }
                val room = LiveKit.create(context.applicationContext)
                room.videoTrackCaptureDefaults = LocalVideoTrackOptions(position = CameraPosition.FRONT)
                room.adaptiveStream = true
                this@VoiceCallStore.room = room
                audioRouting = room.audioSwitchHandler?.let { handler ->
                    CallAudioRouting(handler, sessionScope) { audio ->
                        if (this@VoiceCallStore.room === room) _audio.value = audio
                    }
                }
                eventsJob = sessionScope.launch {
                    room.events.collect { event ->
                        if (this@VoiceCallStore.room !== room) return@collect
                        when (event) {
                            is RoomEvent.Reconnecting -> _state.value = CallState.Reconnecting
                            is RoomEvent.Reconnected -> _state.value = CallState.Connected
                            is RoomEvent.Disconnected -> {
                                cleanup()
                                _publishError.value = "The call disconnected. Please reconnect."
                                return@collect
                            }
                            is RoomEvent.TrackSubscriptionFailed ->
                                _publishError.value = "A stream couldn't be received. Check your connection."
                            else -> Unit
                        }
                        sync(room)
                    }
                }
                room.connect(grant.livekitUrl, grant.token)
                currentCoroutineContext().ensureActive()
                check(room.localParticipant.setMicrophoneEnabled(true)) { "Microphone publication failed" }
                currentCoroutineContext().ensureActive()
                _isMuted.value = false
                _state.value = CallState.Connected
                sync(room)
                reportVoiceState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (callJob === job) {
                    cleanup()
                    _publishError.value = "Couldn't connect to voice. Check permissions and your connection."
                }
            }
        }
    }

    fun toggleMute() {
        if (_state.value != CallState.Connected && _state.value != CallState.Reconnecting) return
        val room = room ?: return
        callScope?.launch {
            controls.withLock {
                try {
                    val newMuted = !_isMuted.value
                    check(room.localParticipant.setMicrophoneEnabled(!newMuted)) { "Microphone update failed" }
                    currentCoroutineContext().ensureActive()
                    _isMuted.value = newMuted
                    sync(room)
                    reportVoiceState()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _publishError.value = "Couldn't change microphone state."
                }
            }
        }
    }

    /** Start/stop streaming the phone camera into the call. Needs the CAMERA runtime grant. */
    fun toggleCamera() {
        val room = room ?: return
        if (_state.value != CallState.Connected) return
        callScope?.launch {
            controls.withLock {
                val enable = !_isCameraOn.value
                try {
                    val local = room.localParticipant
                    if (enable) {
                        if (!cameraForeground) return@withLock
                        check(local.setCameraEnabled(true)) { "Camera publication failed" }
                        currentCoroutineContext().ensureActive()
                        val track = local.getTrackPublication(Track.Source.CAMERA)?.track
                        if (!cameraForeground) {
                            (track as? LocalVideoTrack)?.stopCapture()
                            track?.let { local.unpublishTrack(it) }
                        }
                        _isCameraOn.value = cameraForeground && track != null
                    } else {
                        // Unpublish completely so remote clients remove the tile.
                        local.getTrackPublication(Track.Source.CAMERA)?.track?.let {
                            (it as? LocalVideoTrack)?.stopCapture()
                            local.unpublishTrack(it)
                            // LiveKit caches its default track for the next enable;
                            // Room.release() owns disposal at call teardown.
                        }
                        _isCameraOn.value = false
                    }
                    sync(room)
                    reportVoiceState()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _publishError.value = "Couldn't ${if (enable) "start" else "stop"} the camera."
                }
            }
        }
    }

    /** Switch between front and back cameras while streaming. */
    fun flipCamera() {
        val local = room?.localParticipant ?: return
        val track = local.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack ?: return
        callScope?.launch {
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
        val channelId = activeChannelId
        // Stop capture NOW, even if the server is unreachable.
        cleanup()
        scope.launch {
            withTimeoutOrNull(5_000) {
                channelId?.let { id -> runCatching { apiCall { api.leaveVoice(id) } } }
            }
        }
    }

    /** Sign-out must not leave a LiveKit session recording independently of REST auth. */
    fun stopForSignOut() = cleanup()

    /** Camera has no background FGS: stop it as the activity leaves the foreground. */
    fun stopCameraForBackground() {
        val track = room?.localParticipant?.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
        track?.stopCapture()
        if (_isCameraOn.value) toggleCamera()
    }

    /**
     * Best-effort report of mic/camera/screen state so server occupancy and
     * every other client's roster reflect it (LiveKit only carries the media).
     */
    private fun reportVoiceState() {
        // A slow occupancy request must never hold the mic/camera control mutex.
        stateReportJob?.cancel()
        stateReportJob = callScope?.launch {
            runCatching {
                apiCall {
                    api.updateVoiceState(
                        VoiceStateUpdateRequest(
                            muted = _isMuted.value,
                            video = _isCameraOn.value,
                            screen = false,
                        ),
                    )
                }
            }
        }
    }

    private fun cleanup() {
        sessionGeneration++
        callJob?.cancel()
        callJob = null
        callScope = null
        stateReportJob = null
        eventsJob?.cancel()
        eventsJob = null
        audioRouting?.close()
        audioRouting = null
        _audio.value = CallAudioState()
        val oldRoom = room
        room = null
        oldRoom?.disconnect()
        oldRoom?.release()
        activeChannelId = null
        _participants.value = emptyList()
        _liveStreams.value = emptyList()
        _isCameraOn.value = false
        _isMuted.value = false
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
