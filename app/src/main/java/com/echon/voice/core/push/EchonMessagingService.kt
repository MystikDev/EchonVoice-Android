package com.echon.voice.core.push

import com.echon.voice.core.network.EchonJson
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/**
 * Receives FCM data pushes and posts a message notification.
 *
 * Server data-message contract (all FCM data values are strings):
 *   kind    — "dm" for a direct message, otherwise a server channel
 *   id      — the channel/DM id (deep-link target)
 *   payload — a JSON-stringified object with the display fields:
 *             { "title": ..., "body": ..., "name": ... }  (name = channel/DM label)
 *
 * Parsing is tolerant: missing/renamed fields fall back to the RemoteMessage
 * notification block so a slightly different payload still shows *something*.
 * Using data messages (not notification messages) means this fires even when the
 * app is backgrounded, so we always control display + deep link.
 */
@AndroidEntryPoint
class EchonMessagingService : FirebaseMessagingService() {

    @Inject lateinit var registrar: PushTokenRegistrar

    override fun onNewToken(token: String) {
        registrar.onTokenRefreshed(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data

        // Inner payload is JSON-stringified (FCM data values must be strings).
        val payload: Map<String, String> = data["payload"]?.let { raw ->
            runCatching {
                EchonJson.parseToJsonElement(raw).jsonObject
                    .mapValues { it.value.jsonPrimitive.content }
            }.getOrNull()
        } ?: emptyMap()

        val name = payload["name"] ?: payload["channel_name"]
        val title = payload["title"] ?: message.notification?.title ?: name ?: "New message"
        val body = payload["body"] ?: message.notification?.body.orEmpty()

        MessageNotifier.notify(
            context = this,
            channelId = data["id"],
            channelName = name,
            channelKind = data["kind"],
            title = title,
            body = body,
        )
    }
}
