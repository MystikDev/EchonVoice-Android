package com.echon.voice.core.realtime

import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.EchonJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.startCoroutine

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PresenceStoreTest {
    private fun api(
        social: suspend () -> PresenceResponse = { PresenceResponse(emptyMap()) },
        server: suspend (String) -> PresenceResponse = { PresenceResponse(emptyMap()) },
    ): EchonApi = Proxy.newProxyInstance(EchonApi::class.java.classLoader, arrayOf(EchonApi::class.java)) { _, method, args ->
        val call: suspend () -> PresenceResponse = {
            when (method.name) {
                "socialPresence" -> social()
                "serverPresence" -> server(args!![0] as String)
                else -> error("Unexpected API call: ${method.name}")
            }
        }
        @Suppress("UNCHECKED_CAST")
        call.startCoroutine(args!!.last() as Continuation<PresenceResponse>)
        COROUTINE_SUSPENDED
    } as EchonApi

    @Test fun snapshotsPopulateAlreadyOnlineUsersAndSparseOfflineMembers() = runTest {
        val store = PresenceStore(api(
            social = { PresenceResponse(mapOf("friend" to "dnd")) },
            server = { PresenceResponse(mapOf("member" to "online", "away" to "idle")) },
        ), backgroundScope)
        backgroundScope.launch { store.watchServer("server") }
        store.connect()
        runCurrent()
        assertEquals("dnd", store.social.value.status("friend"))
        assertEquals("online", store.servers.value.getValue("server").status("member"))
        assertEquals("idle", store.servers.value.getValue("server").status("away"))
        assertEquals("offline", store.servers.value.getValue("server").status("absent"))
        store.disconnect()
        assertNull(store.servers.value.getValue("server").status("member"))
        assertFalse(store.social.value.loaded)
    }

    @Test fun slowSnapshotCannotUndoNewerSocketEventButNextPollReconcilesIt() = runTest {
        val pending = CompletableDeferred<PresenceResponse>()
        var calls = 0
        val store = PresenceStore(api(social = {
            if (++calls == 1) pending.await() else PresenceResponse(mapOf("member" to "offline"))
        }), backgroundScope)
        store.connect()
        runCurrent()
        store.apply("member", "online")
        pending.complete(PresenceResponse(mapOf("member" to "offline")))
        runCurrent()
        assertEquals("online", store.social.value.status("member"))
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("offline", store.social.value.status("member"))
        assertEquals(2, calls)
    }

    @Test fun failedSnapshotIsUnknownAndRecoversWithoutReconnect() = runTest {
        var failed = true
        val store = PresenceStore(api(social = {
            if (failed) error("network unavailable") else PresenceResponse(emptyMap())
        }), backgroundScope)
        store.connect()
        runCurrent()
        assertTrue(store.social.value.failed)
        assertNull(store.social.value.status("member"))
        failed = false
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("offline", store.social.value.status("member"))
        assertFalse(store.social.value.failed)
    }

    @Test fun disconnectedAccountRejectsLateResponseAndDeltas() = runTest {
        val pending = CompletableDeferred<PresenceResponse>()
        var calls = 0
        val store = PresenceStore(api(social = {
            if (++calls == 1) withContext(NonCancellable) { pending.await() }
            else PresenceResponse(mapOf("new" to "idle"))
        }), backgroundScope)
        store.connect()
        runCurrent()
        store.disconnect()
        store.apply("old", "online")
        assertTrue(store.social.value.statuses.isEmpty())
        store.connect()
        runCurrent()
        pending.complete(PresenceResponse(mapOf("old" to "online")))
        runCurrent()
        assertEquals(mapOf("new" to "idle"), store.social.value.statuses)
    }

    @Test fun serverWatchersShareOnePollAndReleaseItWhenScreenCloses() = runTest {
        var requests = 0
        val store = PresenceStore(api(server = { requests++; PresenceResponse(emptyMap()) }), backgroundScope)
        store.connect()
        val first = backgroundScope.launch { store.watchServer("server") }
        val second = backgroundScope.launch { store.watchServer("server") }
        runCurrent()
        assertEquals(1, requests)
        first.cancel()
        runCurrent()
        assertTrue(store.servers.value.containsKey("server"))
        second.cancel()
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, requests)
        assertFalse(store.servers.value.containsKey("server"))
    }

    @Test fun malformedAndUnknownPresenceDoesNotBecomeOffline() {
        assertTrue(WsEventParser.parse("""{"event":"presence-changed","data":{"user_id":"u"}}""") is WsEvent.Unknown)
        assertTrue(WsEventParser.parse("""{"event":"presence.update","data":{"user_id":"u","status":"future"}}""") is WsEvent.Unknown)
        assertNull(WsEventParser.parse("""{"event":{}}"""))
        assertEquals(WsEvent.PresenceChanged("u", "dnd"), WsEventParser.parse("""{"event":"presence-changed","data":{"user_id":"u","status":"dnd"}}"""))
        val response = EchonJson.decodeFromString<PresenceResponse>("""{"presences":{"u":"future"}}""")
        assertNull(PresenceState(response.presences, loaded = true).status("u"))
        assertEquals("Status unavailable", PresenceStatus.label(null))
    }
}
