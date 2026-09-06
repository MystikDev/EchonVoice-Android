package com.echon.voice.feature.members

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.realtime.PresenceResponse
import com.echon.voice.core.realtime.PresenceStore
import com.echon.voice.model.Member
import com.echon.voice.model.MembersResponse
import com.echon.voice.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

/** Exercises the production member screen and store with deterministic REST data. */
class MemberPresenceScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun populationUpdatesLiveAndDisconnectDoesNotLabelEveryoneOffline() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val api = Proxy.newProxyInstance(EchonApi::class.java.classLoader, arrayOf(EchonApi::class.java)) { _, method, _ ->
            when (method.name) {
                "serverMembers" -> MembersResponse(listOf("Alice", "Bob", "Cara").map { Member(User(it, username = it)) })
                "serverPresence" -> PresenceResponse(mapOf("Alice" to "online", "Bob" to "dnd"))
                "socialPresence" -> PresenceResponse(emptyMap())
                else -> error("Unexpected API request: ${method.name}")
            }
        } as EchonApi
        val store = PresenceStore(api, scope)
        val model = MembersViewModel(SavedStateHandle(mapOf("serverId" to "server")), api, store)
        try {
            compose.setContent { MaterialTheme { MembersScreen({}, {}, model) } }
            compose.onNodeWithText("Status unavailable — 3").assertIsDisplayed()
            compose.runOnIdle { store.connect() }
            compose.onNodeWithText("Online — 2").assertIsDisplayed()
            compose.onNodeWithText("Offline — 1").assertIsDisplayed()
            compose.onNodeWithText("Bob · Do not disturb").assertIsDisplayed()
            compose.runOnIdle { store.apply("Cara", "idle") }
            compose.onNodeWithText("Online — 3").assertIsDisplayed()
            compose.onNodeWithText("Cara · Idle").assertIsDisplayed()
            compose.runOnIdle { store.disconnect() }
            compose.onNodeWithText("Status unavailable — 3").assertIsDisplayed()
            compose.onNodeWithText("Offline — 3").assertDoesNotExist()
            compose.runOnIdle { store.connect() }
            compose.onNodeWithText("Online — 2").assertIsDisplayed()
        } finally {
            store.disconnect()
            scope.cancel()
        }
    }
}
