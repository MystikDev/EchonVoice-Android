package com.echon.voice.feature.servers

import com.echon.voice.core.network.*
import com.echon.voice.core.realtime.*
import com.echon.voice.feature.chat.ChatStores
import com.echon.voice.model.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChannelCompatibilityTest {
    @Test fun updateOfUnknownChannelAddsItAndDeleteRemovesItUntilAccessReturns() {
        val api = Proxy.newProxyInstance(EchonApi::class.java.classLoader, arrayOf(EchonApi::class.java)) { _, _, _ -> error("No network expected") } as EchonApi
        val store = ServersStore(api)
        val event = WsEventParser.parse("""{"event":"channel-updated","data":{"id":"private","server_id":"server","type":"text","name":"Restored"}}""") as WsEvent.ChannelUpserted
        store.upsertChannel(event.channel)
        assertEquals(listOf("private"), store.channelsFor("server").map { it.id })
        store.upsertChannel(event.channel.copy(name = "Renamed"))
        assertEquals(1, store.channelsFor("server").size)
        val deleted = WsEventParser.parse("""{"event":"channel-deleted","data":{"server_id":"server","channel_id":"private"}}""") as WsEvent.ChannelDeleted
        store.removeChannel(deleted.channelId)
        assertTrue(store.channelsFor("server").isEmpty()); assertTrue("private" in store.revokedChannelIds.value)
        store.upsertChannel(event.channel)
        assertEquals(1, store.channelsFor("server").size); assertFalse("private" in store.revokedChannelIds.value)
    }

    @Test fun staleRestResponseCannotRestoreRevokedChannel() = runTest {
        lateinit var reply: Continuation<Any?>
        val api = Proxy.newProxyInstance(EchonApi::class.java.classLoader, arrayOf(EchonApi::class.java)) { _, method, args ->
            check(method.name == "serverChannels")
            @Suppress("UNCHECKED_CAST")
            val continuation = args!!.last() as Continuation<Any?>
            reply = continuation
            kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
        } as EchonApi
        val store = ServersStore(api)
        store.upsertChannel(Channel("private", serverId = "s"))
        val job = launch { store.loadChannels("s") }; runCurrent()
        store.removeChannel("private")
        reply.resume(ChannelsResponse(listOf(Channel("private", serverId = "s"))))
        job.join()
        assertTrue(store.channelsFor("s").isEmpty())
    }

    @Test fun restSnapshotRevokesMissingChannelsAndRestoresReturnedOnes() = kotlinx.coroutines.runBlocking {
        var visible = emptyList<Channel>()
        val api = Proxy.newProxyInstance(EchonApi::class.java.classLoader, arrayOf(EchonApi::class.java)) { _, method, _ ->
            check(method.name == "serverChannels"); ChannelsResponse(visible)
        } as EchonApi
        val store = ServersStore(api)
        val channel = Channel("private", serverId = "s")
        store.upsertChannel(channel)
        store.loadChannels("s")
        assertTrue("private" in store.revokedChannelIds.value)
        visible = listOf(channel)
        store.loadChannels("s")
        assertFalse("private" in store.revokedChannelIds.value)
        assertEquals(listOf(channel), store.channelsFor("s"))
    }

    @Test fun channelRevocationClearsExistingMessageViewAndCannotBeRepopulated() {
        val api = Proxy.newProxyInstance(EchonApi::class.java.classLoader, arrayOf(EchonApi::class.java)) { _, _, _ -> error("No network") } as EchonApi
        val chats = ChatStores(api)
        val oldView = chats.store("private")
        oldView.applyRemote(Message("m", channelId = "private", content = "secret"))
        chats.revoke("private")
        oldView.applyRemote(Message("late", channelId = "private", content = "late"))
        assertTrue(oldView.messages.value.isEmpty())
        assertTrue(chats.store("private").messages.value.isEmpty())
    }
}
