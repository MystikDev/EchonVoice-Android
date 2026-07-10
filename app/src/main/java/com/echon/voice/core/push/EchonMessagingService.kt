package com.echon.voice.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives FCM pushes. Data-message payload contract (backend sends these keys):
 *   channel_id, channel_name, channel_kind ("dm" | anything else = server),
 *   title, body. Falls back to the notification block if data keys are absent.
 *
 * Using data messages (not notification messages) means [onMessageReceived]
 * fires even when the app is backgrounded, so we always control the notification
 * and its deep link.
 */
@AndroidEntryPoint
class EchonMessagingService : FirebaseMessagingService() {

    @Inject lateinit var registrar: PushTokenRegistrar

    override fun onNewToken(token: String) {
        registrar.onTokenRefreshed(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: "New message"
        val body = data["body"] ?: message.notification?.body ?: ""
        MessageNotifier.notify(
            context = this,
            channelId = data["channel_id"],
            channelName = data["channel_name"],
            channelKind = data["channel_kind"],
            title = title,
            body = body,
        )
    }
}
