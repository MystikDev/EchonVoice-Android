package com.echon.voice.core.realtime

import com.echon.voice.core.network.EchonApi
import com.echon.voice.model.TicketResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class WsClientTest {
    @Test fun initialReadyAndPresenceArriveAndJsonHeartbeatReachesServer() = runBlocking {
        val server = MockWebServer()
        val ping = CompletableDeferred<String>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"ready"}""")
                webSocket.send("""{"event":"presence-changed","data":{"user_id":"member","status":"online"}}""")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                ping.complete(text)
                webSocket.send("""{"type":"pong"}""")
            }
        }))
        server.start()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().url(server.url("/v1/ws")).build())
        }.build()
        val api = Proxy.newProxyInstance(EchonApi::class.java.classLoader, arrayOf(EchonApi::class.java)) { _, method, _ ->
            check(method.name == "wsTicket")
            TicketResponse("test-ticket")
        } as EchonApi
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val ws = WsClient(api, client, scope)
        val ready = CompletableDeferred<Unit>()
        val presence = CompletableDeferred<WsEvent.PresenceChanged>()
        val consumer = launch(start = CoroutineStart.UNDISPATCHED) {
            ws.events.collect {
                if (it == WsEvent.Ready) ready.complete(Unit)
                if (it is WsEvent.PresenceChanged) presence.complete(it)
            }
        }
        try {
            ws.start()
            withTimeout(25_000) {
                ready.await()
                assertEquals(WsEvent.PresenceChanged("member", "online"), presence.await())
                assertEquals("""{"type":"ping"}""", ping.await())
            }
        } finally {
            ws.stop()
            consumer.cancel()
            scope.cancel()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            server.shutdown()
        }
    }
}
