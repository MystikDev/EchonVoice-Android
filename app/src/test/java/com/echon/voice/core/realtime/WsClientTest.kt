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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
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
    @Test fun desiredChannelsCanBeRejoinedAfterReadyInsteadOfDuringHandshake() = runBlocking {
        val server = MockWebServer()
        val joined = CompletableDeferred<String>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"ready"}""")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("channel:join")) joined.complete(text)
            }
        }))
        server.start()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().url(server.url("/v1/ws")).build())
        }.build()
        val api = Proxy.newProxyInstance(EchonApi::class.java.classLoader, arrayOf(EchonApi::class.java)) { _, method, _ ->
            check(method.name == "wsTicket")
            TicketResponse("ticket")
        } as EchonApi
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val ws = WsClient(api, client, com.echon.voice.core.network.SessionStore(object : com.echon.voice.core.storage.TokenStorage {
            override var accessToken: String? = "access"
            override var refreshToken: String? = "refresh"
            override fun clear() { accessToken = null; refreshToken = null }
        }), scope)
        val consumer = launch(start = CoroutineStart.UNDISPATCHED) {
            ws.events.collect { if (it == WsEvent.Ready) ws.rejoinAll() }
        }
        try {
            ws.join("dm-channel") // Desired before the socket is ready.
            ws.start()
            val frame = withTimeout(5_000) { joined.await() }
            assertTrue(frame.contains("\"channel_id\":\"dm-channel\""))
        } finally {
            ws.stop(); consumer.cancel(); scope.cancel()
            client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll(); server.shutdown()
        }
    }

    @Test fun revokedCloseAndRejectedHandshakeClearSessionAndNeverReconnect() = runBlocking {
        for (mode in listOf("revoked-close", "rejected-handshake", "disabled-account")) {
            val handshakeRejected = mode == "rejected-handshake"
            val disabled = mode == "disabled-account"
            val server = MockWebServer()
            if (handshakeRejected) server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"token_version_mismatch"}"""))
            else server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.close(4001, "session revoked") }
            }))
            server.start()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/v1/ws")).build())
            }.build()
            val tickets = java.util.concurrent.atomic.AtomicInteger()
            val api = Proxy.newProxyInstance(EchonApi::class.java.classLoader, arrayOf(EchonApi::class.java)) { _, _, _ ->
                tickets.incrementAndGet()
                if (disabled) throw retrofit2.HttpException(retrofit2.Response.error<com.echon.voice.model.TicketResponse>(
                    403, """{"error":"account_disabled"}""".toResponseBody("application/json".toMediaType()),
                ))
                TicketResponse("ticket")
            } as EchonApi
            val storage = object : com.echon.voice.core.storage.TokenStorage {
                override var accessToken: String? = "access"
                override var refreshToken: String? = "refresh"
                override fun clear() { accessToken = null; refreshToken = null }
            }
            val session = com.echon.voice.core.network.SessionStore(storage)
            val revoked = CompletableDeferred<Unit>()
            val consumer = launch(start = CoroutineStart.UNDISPATCHED) { session.unauthorized.collect { revoked.complete(Unit) } }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val ws = WsClient(api, client, session, scope)
            try {
                ws.start()
                withTimeout(5_000) { revoked.await() }
                assertFalse(session.hasSession); assertNull(storage.accessToken); assertNull(storage.refreshToken)
                kotlinx.coroutines.delay(5_200) // Longer than the normal reconnect backoff.
                assertEquals(1, tickets.get()); assertEquals(if (disabled) 0 else 1, server.requestCount)
            } finally {
                ws.stop(); consumer.cancel(); scope.cancel()
                client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll(); server.shutdown()
            }
        }
    }

    @Test fun expiredTicketReconnectsWithFreshTicketWithoutClearingHealthySession() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid_ticket"}"""))
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send("""{"type":"ready"}""") }
        }))
        server.start()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().url(server.url("/v1/ws")).build())
        }.build()
        val tickets = java.util.concurrent.atomic.AtomicInteger()
        val api = Proxy.newProxyInstance(EchonApi::class.java.classLoader, arrayOf(EchonApi::class.java)) { _, _, _ ->
            TicketResponse("ticket-${tickets.incrementAndGet()}")
        } as EchonApi
        val session = com.echon.voice.core.network.SessionStore(object : com.echon.voice.core.storage.TokenStorage {
            override var accessToken: String? = "access"
            override var refreshToken: String? = "refresh"
            override fun clear() { accessToken = null; refreshToken = null }
        })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val ws = WsClient(api, client, session, scope)
        val ready = CompletableDeferred<Unit>()
        val consumer = launch(start = CoroutineStart.UNDISPATCHED) { ws.events.collect { if (it == WsEvent.Ready) ready.complete(Unit) } }
        try {
            ws.start(); withTimeout(10_000) { ready.await() }
            assertTrue(session.hasSession); assertEquals("access", session.accessToken); assertEquals(2, tickets.get())
        } finally {
            ws.stop(); consumer.cancel(); scope.cancel()
            client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll(); server.shutdown()
        }
    }

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
        val ws = WsClient(api, client, com.echon.voice.core.network.SessionStore(object : com.echon.voice.core.storage.TokenStorage {
            override var accessToken: String? = "access"
            override var refreshToken: String? = "refresh"
            override fun clear() { accessToken = null; refreshToken = null }
        }), scope)
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
