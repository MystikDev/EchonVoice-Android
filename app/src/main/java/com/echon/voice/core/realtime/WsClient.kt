package com.echon.voice.core.realtime

import com.echon.voice.core.di.ApplicationScope
import com.echon.voice.core.network.ApiConfig
import com.echon.voice.core.network.EchonApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
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
import com.echon.voice.core.network.EchonJson
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.TimeoutCancellationException
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
        socket?.send(EchonJson.encodeToString(frame))
    }

    /** Resume after background/network suspension with a fresh server snapshot. */
    fun reconnect() { socket?.cancel() }

    private suspend fun loop() {
        while (currentCoroutineContext().isActive) {
            val closed = CompletableDeferred<Unit>()
            val opened = CompletableDeferred<Unit>()
            val incoming = Channel<Unit>(Channel.CONFLATED)
            var connection: WebSocket? = null
            val connectionJob = currentCoroutineContext()[Job]!!
            try {
                val ticket = api.wsTicket().ticket
                // Percent-encode the ticket so reserved characters (+, /, =) in a
                // non-URL-safe token can't corrupt the query string.
                val encodedTicket = java.net.URLEncoder.encode(ticket, "UTF-8")
                val request = Request.Builder()
                    .url("${ApiConfig.WS_URL}?ticket=$encodedTicket")
                    .build()
                connection = client.newWebSocket(request, listener(closed, opened, incoming, connectionJob))
                socket = connection
                coroutineScope {
                    val heartbeat = launch {
                        try {
                            maintainWsHeartbeat(opened, incoming) {
                                connection.send(EchonJson.encodeToString(mapOf("type" to "ping")))
                            }
                        } catch (_: TimeoutCancellationException) {
                            closed.complete(Unit)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            closed.complete(Unit)
                        }
                    }
                    try { closed.await() } finally { heartbeat.cancel() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ticket fetch / connect failure → fall through to backoff.
            } finally {
                closed.complete(Unit)
                incoming.close()
                connection?.cancel() // Includes cancellation during the HTTP handshake.
                if (socket === connection) socket = null
            }
            if (!currentCoroutineContext().isActive) break
            _events.tryEmit(WsEvent.SocketDisconnected)
            delay(BACKOFF_MS)
        }
    }

    private fun listener(
        closed: CompletableDeferred<Unit>,
        opened: CompletableDeferred<Unit>,
        incoming: Channel<Unit>,
        connectionJob: Job,
    ) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!connectionJob.isActive || closed.isCompleted) {
                webSocket.cancel()
                return
            }
            socket = webSocket
            opened.complete(Unit)
            // Surface connect first so stores can REST-reconcile dropped frames,
            // then re-subscribe to every previously-joined channel.
            _events.tryEmit(WsEvent.SocketConnected)
            synchronized(joinedChannels) {
                joinedChannels.forEach {
                    webSocket.send(EchonJson.encodeToString(mapOf("type" to "channel:join", "channel_id" to it)))
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!connectionJob.isActive || closed.isCompleted || socket !== webSocket) return
            incoming.trySend(Unit)
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
