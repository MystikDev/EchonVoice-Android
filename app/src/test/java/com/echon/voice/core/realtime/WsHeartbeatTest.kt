package com.echon.voice.core.realtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WsHeartbeatTest {
    @Test fun sendsPingsAndTimesOutThirtyFiveSecondsAfterLastFrame() = runTest {
        val opened = CompletableDeferred(Unit)
        val incoming = Channel<Unit>(Channel.CONFLATED)
        var pings = 0
        var failure: Throwable? = null
        backgroundScope.launch {
            try { maintainWsHeartbeat(opened, incoming) { pings++; true } }
            catch (e: Exception) { failure = e }
        }
        runCurrent()
        advanceTimeBy(15_000); runCurrent()
        assertEquals(1, pings)
        incoming.trySend(Unit); runCurrent()
        advanceTimeBy(34_999); runCurrent()
        assertNull(failure)
        advanceTimeBy(1); runCurrent()
        assertNotNull(failure)
        val finalPings = pings
        advanceTimeBy(60_000); runCurrent()
        assertEquals(finalPings, pings)
    }

    @Test fun stalledHandshakeHasBoundedLifetime() = runTest {
        var expired = false
        backgroundScope.launch {
            try { maintainWsHeartbeat(CompletableDeferred(), Channel(Channel.CONFLATED)) { error("Not open") } }
            catch (_: Exception) { expired = true }
        }
        runCurrent()
        advanceTimeBy(20_000); runCurrent()
        assertTrue(expired)
    }

    @Test fun cancellationStopsHeartbeat() = runTest {
        var pings = 0
        val job = backgroundScope.launch {
            maintainWsHeartbeat(CompletableDeferred(Unit), Channel(Channel.CONFLATED)) { pings++; true }
        }
        runCurrent()
        job.cancel(); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(0, pings)
    }
}
