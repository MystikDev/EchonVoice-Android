package com.echon.voice.feature.voice

import android.media.AudioManager
import com.twilio.audioswitch.AudioDevice
import com.twilio.audioswitch.AudioDeviceChangeListener
import io.livekit.android.audio.AudioSwitchHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Selected is the SDK-reported route, not an optimistic button state. */
data class CallAudioState(
    val devices: List<AudioDevice> = emptyList(),
    val selected: AudioDevice? = null,
    val automatic: Boolean = true,
    val interrupted: Boolean = false,
)

/** One call owns the listeners; queued callbacks cannot update a later call. */
internal class CallAudioRouting(
    private val handler: AudioSwitchHandler,
    private val scope: CoroutineScope,
    private val changed: (CallAudioState) -> Unit,
) : AutoCloseable {
    @Volatile private var closed = false
    private var state = CallAudioState()
    private var requested: AudioDevice? = null
    private val devices = object : AudioDeviceChangeListener {
        override fun invoke(audioDevices: List<AudioDevice>, selectedAudioDevice: AudioDevice?) {
            val snapshot = audioDevices.toList()
            scope.launch(Dispatchers.Main.immediate) {
                if (!closed) {
                    if (requested !in snapshot) requested = null
                    update(state.copy(devices = snapshot, selected = selectedAudioDevice, automatic = requested == null))
                    applySelection()
                }
            }
        }
    }
    private val focus = AudioManager.OnAudioFocusChangeListener { change ->
        scope.launch(Dispatchers.Main.immediate) {
            if (!closed) update(state.copy(interrupted = change != AudioManager.AUDIOFOCUS_GAIN))
        }
    }

    init {
        // Preserve communication mode, echo cancellation, and the SDK's Bluetooth /
        // wired / speaker priority. Do not repeatedly override routing on room events.
        handler.loggingEnabled = false
        handler.registerAudioDeviceChangeListener(devices)
        handler.registerOnAudioFocusChangeListener(focus)
    }

    /** null returns to automatic routing, including future headset hot-plug. */
    fun select(device: AudioDevice?) {
        if (closed || (device != null && device !in state.devices)) return
        requested = device
        update(state.copy(automatic = device == null))
        applySelection()
    }

    private fun applySelection() {
        // The pinned AudioSwitch implementation's selectDevice(null) clears the
        // selected route without immediately choosing a replacement. Resolve an
        // actual available device instead; device callbacks keep Automatic current.
        // The SDK provides this list in its configured headset-first priority order.
        val target = requested ?: state.devices.firstOrNull() ?: return
        if (state.selected != target) handler.selectDevice(target)
    }

    private fun update(next: CallAudioState) { state = next; changed(next) }

    override fun close() {
        closed = true
        handler.unregisterAudioDeviceChangeListener(devices)
        handler.unregisterOnAudioFocusChangeListener(focus)
        // Room.release owns stop/focus abandonment; do not start or stop audio here.
    }
}
