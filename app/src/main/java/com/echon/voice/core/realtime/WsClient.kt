package com.echon.voice.core.realtime

import com.echon.voice.core.di.ApplicationScope
import com.echon.voice.core.network.ApiConfig
import com.echon.voice.core.network.EchonApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket to /v1/ws with ticket auth, automatic reconnect (5s backoff, fresh
 * ticket each attempt), and channel re-join on reconnect. Frames flow out via
 * [events]; consumers must treat the socket as lossy (the server drops frames to
 * slow clients under backpressure) and REST-reconcile on [WsEvent.SocketConnected].
 */
@Singleton
class WsClient @Inject constructor(
    private val api: EchonApi,
    private val client: OkHttpClient,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 512)
    val events: SharedFlow<WsEvent> = _events

    private val joinedChannels = Collections.synchronizedSet(mutableSetOf<String>())

    @Volatile private var socket: WebSocket? = null
    private var runJob: Job? = null

    fun start() {
        if (runJob != null) return
        runJob = scope.launch { loop() }
    }

    suspend fun stop() {
        runJob?.cancelAndJoin()
        runJob = null
        socket?.close(1000, null)
        socket = null
        joinedChannels.clear()
        _events.tryEmit(WsEvent.SocketDisconnected)
    }

    fun join(channelId: String) {
        joinedChannels.add(channelId)
        send(mapOf("type" to "channel:join", "channel_id" to channelId))
    }

    fun leave(channelId: String) {
        joinedChannels.remove(channelId)
        send(mapOf("type" to "channel:leave", "channel_id" to channelId))
    }

    fun sendTyping(channelId: String, isTyping: Boolean) {
        send(mapOf("type" to if (isTyping) "typing:start" else "typing:stop", "channel_id" to channelId))
    }

    private fun send(frame: Map<String, String>) {
        socket?.send(JSONObject(frame).toString())
    }

    private suspend fun loop() {
        while (scope.isActive && runJob?.isActive != false) {
            val closed = CompletableDeferred<Unit>()
            try {
                val ticket = api.wsTicket().ticket
                val request = Request.Builder()
                    .url("${ApiConfig.WS_URL}?ticket=$ticket")
                    .build()
                client.newWebSocket(request, listener(closed))
                closed.await() // suspends until this connection drops
            } catch (e: Exception) {
                // Ticket fetch / connect failure → fall through to backoff.
            }
            socket = null
            if (!scope.isActive) break
            _events.tryEmit(WsEvent.SocketDisconnected)
            delay(BACKOFF_MS)
        }
    }

    private fun listener(closed: CompletableDeferred<Unit>) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            socket = webSocket
            // Surface connect first so stores can REST-reconcile dropped frames,
            // then re-subscribe to every previously-joined channel.
            _events.tryEmit(WsEvent.SocketConnected)
            synchronized(joinedChannels) {
                joinedChannels.forEach {
                    webSocket.send(JSONObject(mapOf("type" to "channel:join", "channel_id" to it)).toString())
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            WsEventParser.parse(text)?.let { _events.tryEmit(it) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            if (!closed.isCompleted) closed.complete(Unit)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!closed.isCompleted) closed.complete(Unit)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!closed.isCompleted) closed.complete(Unit)
        }
    }

    private companion object {
        const val BACKOFF_MS = 5_000L
    }
}
