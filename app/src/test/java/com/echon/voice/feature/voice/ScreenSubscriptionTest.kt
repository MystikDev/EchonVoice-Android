package com.echon.voice.feature.voice

import org.junit.Assert.*
import org.junit.Test

class ScreenSubscriptionTest {
    @Test fun screenShareIsOptInAndMutingItsAudioLeavesVoiceSubscribed() {
        val screen = "owner-video"
        assertFalse(shouldReceiveTrack(true, false, screen, listOf(screen), emptySet(), emptySet()))
        assertFalse(shouldReceiveTrack(false, true, "owner-audio", listOf(screen), emptySet(), emptySet()))
        assertTrue(shouldReceiveTrack(true, false, screen, listOf(screen), setOf(screen), emptySet()))
        assertTrue(shouldReceiveTrack(false, true, "owner-audio", listOf(screen), setOf(screen), emptySet()))
        assertFalse(shouldReceiveTrack(false, true, "owner-audio", listOf(screen), setOf(screen), setOf(screen)))
        assertTrue(shouldReceiveTrack(false, false, "voice-microphone", listOf(screen), setOf(screen), setOf(screen)))
        assertTrue(shouldReceiveTrack(false, false, "camera", emptyList(), emptySet(), emptySet()))
    }
}
