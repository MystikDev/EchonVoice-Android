package com.echon.voice.feature.invites

import com.echon.voice.core.network.EchonApi
import com.echon.voice.feature.servers.ServersStore
import com.echon.voice.model.InvitePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class InviteViewModelTest {
    @Test fun clearingOrChangingInputInvalidatesPreviewAndCancelsDebounce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val requests = mutableListOf<String>()
            val api = Proxy.newProxyInstance(EchonApi::class.java.classLoader, arrayOf(EchonApi::class.java)) { _, method, args ->
                check(method.name == "previewInvite")
                val code = args!![0] as String
                requests += code
                InvitePreview(code)
            } as EchonApi
            val model = InviteViewModel(api, ServersStore(api))
            model.preview("old"); advanceTimeBy(100)
            model.preview("new"); advanceUntilIdle()
            assertEquals(listOf("new"), requests)
            assertTrue(model.canJoin("new")); assertFalse(model.canJoin("old"))
            model.preview(""); advanceUntilIdle()
            assertNull(model.preview); assertFalse(model.canJoin("new")); assertFalse(model.busy)
            model.preview("next"); model.reset(); advanceUntilIdle()
            assertEquals(listOf("new"), requests)
        } finally { Dispatchers.resetMain() }
    }
}
