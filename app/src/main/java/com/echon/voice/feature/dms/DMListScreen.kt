package com.echon.voice.feature.dms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echon.voice.core.designsystem.AvatarWithPresence
import com.echon.voice.core.realtime.RealtimeStore
import com.echon.voice.feature.auth.AuthStore
import com.echon.voice.feature.moderation.BlocksStore
import com.echon.voice.model.DMConversation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DMsViewModel @Inject constructor(
    private val dms: DMsStore,
    blocks: BlocksStore,
    private val auth: AuthStore,
    private val realtime: RealtimeStore,
) : ViewModel() {
    val myId: String? get() = auth.currentUser.value?.id
    val presence = realtime.presence

    val conversations: StateFlow<List<DMConversation>> =
        combine(dms.conversations, blocks.blockedIds, auth.currentUser) { convos, blocked, me ->
            convos.filter { convo ->
                if (convo.isGroup == true) return@filter true
                val other = convo.other(me?.id) ?: return@filter true
                other.id !in blocked
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() = viewModelScope.launch {
        try {
            dms.load()
            // The server only streams a DM after its conversation id is joined.
            // Refreshing the list may discover conversations created elsewhere.
            realtime.joinDMChannels()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Socket Ready and the next tab visit retry.
        }
    }
}

@Composable
fun DMListScreen(
    onOpenDm: (channelId: String, name: String) -> Unit,
    viewModel: DMsViewModel = hiltViewModel(),
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val presence by viewModel.presence.collectAsStateWithLifecycle()

    // MainScaffold keeps this ViewModel alive while switching tabs, so init alone
    // is insufficient: refresh and rejoin whenever the DMs tab re-enters composition.
    androidx.compose.runtime.LaunchedEffect(viewModel) { viewModel.refresh().join() }

    if (conversations.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No conversations yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(conversations, key = { it.id }) { convo ->
            val other = convo.other(viewModel.myId)
            val name = convo.displayName(viewModel.myId)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDm(convo.id, name) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // As on iOS, DM rows only show a dot once presence is known
                // (group DMs have no single "other" user, so none there).
                AvatarWithPresence(
                    user = other,
                    status = other?.id?.let { presence.status(it) },
                    size = 44.dp,
                    showWhenUnknown = false,
                )
                Column {
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    other?.displayHandle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider()
        }
    }
}
