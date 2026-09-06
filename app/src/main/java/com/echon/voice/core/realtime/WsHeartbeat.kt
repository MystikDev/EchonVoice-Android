package com.echon.voice.core.realtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Matches the web client's JSON ping (15s) and missing-frame deadline (35s). */
internal suspend fun maintainWsHeartbeat(
    opened: CompletableDeferred<Unit>,
    incoming: Channel<Unit>,
    sendPing: () -> Boolean,
) = coroutineScope {
    withTimeout(20_000) { opened.await() }
    val sender = launch {
        while (isActive) {
            delay(15_000)
            check(sendPing()) { "WebSocket could not queue heartbeat" }
        }
    }
    try {
        while (isActive) withTimeout(35_000) { incoming.receive() }
    } finally {
        sender.cancel()
    }
}
