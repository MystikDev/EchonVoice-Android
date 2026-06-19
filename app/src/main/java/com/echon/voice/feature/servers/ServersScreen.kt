package com.echon.voice.feature.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.echon.voice.core.designsystem.EchonColors
import com.echon.voice.core.realtime.RealtimeStore
import com.echon.voice.model.ChannelKind
import com.echon.voice.model.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val servers: ServersStore,
    realtime: RealtimeStore,
) : ViewModel() {
    val serverList = servers.servers
    val selectedServerId = servers.selectedServerId
    val channelsByServer = servers.channelsByServer
    val unread = realtime.unreadChannelIds

    fun select(serverId: String) = viewModelScope.launch { servers.select(serverId) }
    fun channelsFor(serverId: String?) = servers.channelsFor(serverId)
}

@Composable
fun ServersScreen(
    onOpenChannel: (channelId: String, channelName: String) -> Unit,
    onOpenMembers: (serverId: String) -> Unit,
    viewModel: ServersViewModel = hiltViewModel(),
) {
    val servers by viewModel.serverList.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedServerId.collectAsStateWithLifecycle()
    val channelsByServer by viewModel.channelsByServer.collectAsStateWithLifecycle()
    val unread by viewModel.unread.collectAsStateWithLifecycle()

    var showJoin by remember { mutableStateOf(false) }
    var inviteChannelId by remember { mutableStateOf<String?>(null) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Server rail
        Column(
            modifier = Modifier
                .width(72.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            servers.forEach { server ->
                ServerIcon(server, selected = server.id == selectedId) { viewModel.select(server.id) }
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .clickable { showJoin = true },
                contentAlignment = Alignment.Center,
            ) { Text("+", color = EchonColors.Primary, fontWeight = FontWeight.Bold) }
        }

        // Channel list (observed so it recomposes when channels finish loading)
        val channels = (channelsByServer[selectedId] ?: emptyList()).sortedBy { it.position ?: 0 }
        val text = channels.filter { it.type == ChannelKind.TEXT }
        val voice = channels.filter { it.type == ChannelKind.VOICE }
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            selectedId?.let { sid ->
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Members", color = EchonColors.Primary, modifier = Modifier.clickable { onOpenMembers(sid) })
                        Text("Invite", color = EchonColors.Primary, modifier = Modifier.clickable { inviteChannelId = text.firstOrNull()?.id })
                    }
                }
            }
            items(text, key = { it.id }) { channel ->
                ChannelRow(
                    prefix = "#",
                    name = channel.name ?: "channel",
                    hasUnread = channel.id in unread,
                ) { onOpenChannel(channel.id, channel.name ?: "channel") }
            }
            if (voice.isNotEmpty()) {
                item { SectionLabel("Voice") }
                items(voice, key = { it.id }) { channel ->
                    ChannelRow(prefix = "🔊", name = channel.name ?: "voice", hasUnread = false) { }
                }
            }
        }
    }

    if (showJoin) {
        com.echon.voice.feature.invites.JoinServerSheet(onDismiss = { showJoin = false })
    }
    inviteChannelId?.let { cid ->
        com.echon.voice.feature.invites.CreateInviteSheet(channelId = cid, onDismiss = { inviteChannelId = null })
    }
}

@Composable
private fun ServerIcon(server: Server, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(if (selected) 14.dp else 24.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(if (selected) EchonColors.Primary else MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val icon = server.iconUrl
        if (icon != null) {
            AsyncImage(model = icon, contentDescription = server.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(shape))
        } else {
            Text(server.initials, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun ChannelRow(prefix: String, name: String, hasUnread: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(prefix, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            name,
            modifier = Modifier.weight(1f),
            fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal,
        )
        if (hasUnread) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(EchonColors.Primary))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
    )
}
