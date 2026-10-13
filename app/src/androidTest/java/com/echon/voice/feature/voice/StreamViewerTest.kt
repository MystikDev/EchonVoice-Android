package com.echon.voice.feature.voice

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.livekit.android.LiveKit
import io.livekit.android.room.track.LocalVideoTrackOptions
import livekit.org.webrtc.CapturerObserver
import livekit.org.webrtc.SurfaceTextureHelper
import livekit.org.webrtc.VideoCapturer
import org.junit.Rule
import org.junit.Test

/** Real LiveKit renderer and Compose dialog; no account, server, microphone or camera needed. */
class StreamViewerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun fullscreenControlsSurviveRotationAndRepeatedRendererDisposal() {
        val room = LiveKit.create(compose.activity.applicationContext)
        val track = room.localParticipant.createVideoTrack(
            name = "test-screen", capturer = EmptyCapturer(), options = LocalVideoTrackOptions(),
        )
        val visible = mutableStateOf(true)
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            MaterialTheme {
                if (visible.value) FullscreenStream(
                    room, LiveStream("test", "Test screen share", true, track),
                ) { visible.value = false }
            }
        }
        compose.activityRule.scenario.onActivity { it.setContent(content = content) }
        try {
            val portrait = compose.onNodeWithTag("stream-viewport").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithText("Zoom out").assertIsNotEnabled()
            compose.onNodeWithText("Zoom in").performClick()
            compose.onNodeWithText("Zoom out").assertIsEnabled()
            compose.onNodeWithText("Fill screen").performClick()
            compose.onNodeWithText("Fit entire video").assertIsDisplayed()
            compose.onNodeWithText("Reset").performClick()
            compose.onNodeWithText("Zoom out").assertIsNotEnabled()
            // ActivityScenario recreates the host. Set the test UI again after rotation,
            // just as the production host recomposes its saved stream selection.
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            compose.activityRule.scenario.onActivity { it.setContent(content = content) }
            compose.waitForIdle()
            compose.onNodeWithText("Close").assertIsDisplayed()
            val landscape = compose.onNodeWithTag("stream-viewport").fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertTrue("Fullscreen must become wider on landscape rotation", landscape.width > portrait.width)
            org.junit.Assert.assertTrue("Landscape must use the resized viewport", landscape.width > landscape.height)
            repeat(5) {
                compose.onNodeWithText("Close").performClick()
                compose.runOnIdle { visible.value = true }
                compose.onNodeWithText("Zoom in").assertIsDisplayed()
            }
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            track.dispose()
            room.release()
        }
    }

    @Test fun screenShareRequiresWatchAndAudioMuteIsIndependent() {
        val watching = mutableStateOf(false)
        val muted = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                StreamViewingControls(LiveStream("share", "Someone", true, null), watching.value, muted.value,
                    watch = { watching.value = it }, mute = { muted.value = it }, fullscreen = {})
            }
        }
        compose.onNodeWithText("Watch screen share").assertIsDisplayed()
        compose.onNodeWithText("Mute share audio").assertDoesNotExist()
        compose.onNodeWithText("Watch screen share").performClick()
        compose.onNodeWithText("Mute share audio").performClick()
        compose.onNodeWithText("Stop watching").assertIsDisplayed()
        compose.onNodeWithText("Unmute share audio").assertIsDisplayed()
        compose.onNodeWithText("Stop watching").performClick()
        compose.onNodeWithText("Watch screen share").assertIsDisplayed()
    }

    private class EmptyCapturer : VideoCapturer {
        override fun initialize(helper: SurfaceTextureHelper?, context: Context?, observer: CapturerObserver?) = Unit
        override fun startCapture(width: Int, height: Int, framerate: Int) = Unit
        override fun stopCapture() = Unit
        override fun changeCaptureFormat(width: Int, height: Int, framerate: Int) = Unit
        override fun dispose() = Unit
        override fun isScreencast() = true
    }
}
