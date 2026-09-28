package com.echon.voice.feature.feedback

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import com.echon.voice.core.push.PushRegistrationStatus
import com.echon.voice.feature.settings.NotificationSettings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.echon.voice.R
import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.push.MessageNotifier
import com.echon.voice.feature.chat.Composer
import com.echon.voice.feature.chat.MessageRow
import com.echon.voice.feature.invites.CreateInviteSheet
import com.echon.voice.feature.invites.InviteViewModel
import com.echon.voice.feature.servers.ServersStore
import com.echon.voice.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

class FeedbackUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalTestApi::class)
    @Test fun enterAddsBlankLinesAndSendPreservesThem() {
        var sent: String? = null
        val text = mutableStateOf("")
        compose.setContent {
            MaterialTheme {
                Column {
                    Composer(text.value, { text.value = it }, { sent = text.value }, {}, {}, false, "test")
                    MessageRow(Message(id = "preview", content = text.value), null, null, {}, {}, {})
                }
            }
        }
        val editor = compose.onNode(hasSetTextAction())
        editor.performClick().performTextInput("First")
        editor.performKeyInput { pressKey(Key.Enter); pressKey(Key.Enter); pressKey(Key.Enter) }
        editor.performTextInput("Second")
        compose.runOnIdle { assertEquals("First\n\n\nSecond", text.value); assertNull(sent) }
        compose.onAllNodesWithText("First\n\n\nSecond").assertCountEquals(2)
        compose.onNodeWithContentDescription("Send").performClick()
        compose.runOnIdle { assertEquals("First\n\n\nSecond", sent) }
    }

    @Test fun voiceOnlyServerCanGenerateAndCopyInvite() {
        var requestedChannel: String? = null
        val api = Proxy.newProxyInstance(EchonApi::class.java.classLoader, arrayOf(EchonApi::class.java)) { _, method, args ->
            check(method.name == "createInvite")
            requestedChannel = args!![0] as String
            Invite("voice-invite")
        } as EchonApi
        val model = InviteViewModel(api, ServersStore(api))
        try {
            compose.setContent { MaterialTheme {
                CreateInviteSheet("server", listOf(Channel("voice", "Lounge", ChannelKind.VOICE)), {}, model)
            } }
            compose.onNodeWithText("voice-invite").assertIsDisplayed()
            compose.runOnIdle { assertEquals("voice", requestedChannel) }
            compose.onNodeWithText("Copy code").performClick()
            compose.onNodeWithText("Copied").assertIsDisplayed()
            compose.onNodeWithText("Share invite").assertIsDisplayed()
        } finally { compose.runOnIdle { model.reset() } }
    }

    @Test fun settingsShowsPushFailureAndAllowsRetryWithoutClaimingDelivery() {
        var retried = false
        val status = mutableStateOf(PushRegistrationStatus.FAILED)
        compose.setContent { MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                NotificationSettings(status.value) { retried = true; status.value = PushRegistrationStatus.REGISTERING }
            }
        } }
        compose.onNodeWithText("Notifications").assertIsDisplayed()
        compose.onNodeWithText("Couldn't connect message notifications. Check your connection and retry.").assertIsDisplayed()
        compose.onNodeWithText("Open Android notification settings").assertIsDisplayed()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            java.io.File(compose.activity.externalCacheDir, "feedback-notifications.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithText("Retry push registration").performClick()
        compose.runOnIdle { assertTrue(retried) }
        compose.onNodeWithText("Connecting message notifications…").assertIsDisplayed()
        compose.onNodeWithText("Retry push registration").assertIsNotEnabled()
    }

    @Test fun notificationUsesTransparentBellAndPreservesMultilinePreview() {
        val context = compose.activity
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 33) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
        }
        MessageNotifier.ensureChannel(context)
        try {
            assertTrue(MessageNotifier.canPostNotifications(context))
            MessageNotifier.notify(context, null, null, null, "Echon test", "First\n\nSecond")
            val posted = manager.activeNotifications.single { it.id == 0 }.notification
            assertEquals(R.drawable.ic_notification, posted.smallIcon.resId)
            assertEquals("First\n\nSecond", posted.extras.getCharSequence("android.text").toString())
        } finally { manager.cancel(0) }
    }
}
