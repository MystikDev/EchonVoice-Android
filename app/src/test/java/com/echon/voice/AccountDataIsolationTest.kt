package com.echon.voice

import com.echon.voice.core.network.EchonApi
import com.echon.voice.feature.chat.ChatStores
import com.echon.voice.feature.dms.DMsStore
import com.echon.voice.model.DMConversation
import com.echon.voice.model.Message
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume

class AccountDataIsolationTest {
    @Test fun nextAccountCannotReadPreviousChatRegistry() {
        val api = Proxy.newProxyInstance(EchonApi::class.java.classLoader, arrayOf(EchonApi::class.java)) { _, _, _ ->
            error("No network expected")
        } as EchonApi
        val stores = ChatStores(api)
        stores.store("private").applyRemote(Message(id = "secret", content = "private message"))
        stores.clear()
        assertTrue(stores.store("private").messages.value.isEmpty())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun delayedDmLoadCannotRepopulateClearedAccount() = runTest {
        lateinit var continuation: Continuation<List<DMConversation>>
        val api = Proxy.newProxyInstance(EchonApi::class.java.classLoader, arrayOf(EchonApi::class.java)) { _, method, args ->
            check(method.name == "myDms")
            @Suppress("UNCHECKED_CAST")
            continuation = args!!.last() as Continuation<List<DMConversation>>
            COROUTINE_SUSPENDED
        } as EchonApi
        val store = DMsStore(api)
        val job = launch { store.load() }
        runCurrent()
        store.clear()
        continuation.resume(listOf(DMConversation("previous-account-dm")))
        job.join()
        assertTrue(store.conversations.value.isEmpty())
    }
}
