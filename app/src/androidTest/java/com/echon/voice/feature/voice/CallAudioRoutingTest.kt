package com.echon.voice.feature.voice

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.twilio.audioswitch.AudioDevice
import io.livekit.android.LiveKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/** Exercises real SDK/Android communication routing. It does not certify audible media quality. */
class CallAudioRoutingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun speakerEarpieceAndAutomaticRoutingSurviveRepeatedCallLifetimes() {
        repeat(3) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val room = LiveKit.create(compose.activity.applicationContext)
            val handler = checkNotNull(room.audioSwitchHandler)
            val observed = AtomicReference(CallAudioState())

            var callbacks = 0
            lateinit var routing: CallAudioRouting
            compose.runOnIdle {
                routing = CallAudioRouting(handler, scope) { observed.set(it); callbacks++ }
                handler.start() // No network or recording: test the routing component directly.
            }
            try {
                try {
                    compose.waitUntil(15_000) { observed.get().selected != null && observed.get().devices.isNotEmpty() }
                } catch (e: Exception) {
                    throw AssertionError("Routing iteration $it: callbacks=$callbacks, observed=${observed.get()}, SDK selected=${handler.selectedAudioDevice}, SDK available=${handler.availableAudioDevices}", e)
                }
                val speaker = observed.get().devices.filterIsInstance<AudioDevice.Speakerphone>().first()
                compose.runOnIdle { routing.select(speaker) }
                compose.waitUntil(10_000) { observed.get().selected == speaker && !observed.get().automatic }
                observed.get().devices.filterIsInstance<AudioDevice.Earpiece>().firstOrNull()?.let { earpiece ->
                    compose.runOnIdle { routing.select(earpiece) }
                    compose.waitUntil(10_000) { observed.get().selected == earpiece }
                }
                compose.runOnIdle { routing.select(null) }
                compose.waitUntil(10_000) { observed.get().automatic && observed.get().selected == speaker }
                compose.runOnIdle {
                    routing.close()
                    val before = callbacks
                    routing.select(speaker)
                    assertEquals("Closed call cannot accept route changes", before, callbacks)
                }
            } finally {
                compose.runOnIdle { routing.close(); handler.stop(); room.release(); scope.cancel() }
            }
        }
    }
}
