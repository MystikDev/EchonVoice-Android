package com.echon.voice.core.storage

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import coil.imageLoader
import com.echon.voice.core.push.DeepLinkStore
import com.echon.voice.feature.chat.ChatStores
import com.echon.voice.feature.dms.DMsStore
import com.echon.voice.feature.friends.FriendsStore
import com.echon.voice.feature.moderation.BlocksStore
import com.echon.voice.feature.servers.ServersStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Singleton UI stores must never carry one account's private data into the next login. */
@Singleton
class AccountData @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chats: ChatStores,
    private val dms: DMsStore,
    private val friends: FriendsStore,
    private val blocks: BlocksStore,
    private val servers: ServersStore,
    private val links: DeepLinkStore,
) {
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clear() {
        chats.clear()
        dms.clear()
        friends.clear()
        blocks.clear()
        servers.clear()
        links.consume()
        context.imageLoader.memoryCache?.clear()
        context.imageLoader.diskCache?.clear() // Purges caches made by earlier versions too.
        NotificationManagerCompat.from(context).cancelAll()
    }
}
