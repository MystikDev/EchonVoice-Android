package com.echon.voice.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echon.voice.core.realtime.RealtimeStore
import com.echon.voice.feature.dms.DMListScreen
import com.echon.voice.feature.friends.FriendsScreen
import com.echon.voice.feature.servers.ServersScreen
import com.echon.voice.feature.servers.ServersStore
import com.echon.voice.feature.settings.SettingsScreen
import com.echon.voice.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val servers: ServersStore,
    private val realtime: RealtimeStore,
) : ViewModel() {
    init {
        viewModelScope.launch {
            runCatching { servers.loadServers() }
            realtime.start()
        }
    }
}

/** Signed-in tab scaffold: Servers, DMs, Friends, Settings. */
@Composable
fun MainScaffold(
    onOpenChannel: (channelId: String, name: String, kind: String) -> Unit,
    onOpenMembers: (serverId: String) -> Unit,
    onOpenProfile: (User) -> Unit,
    onOpenBlockedUsers: () -> Unit,
    @Suppress("UNUSED_PARAMETER") viewModel: MainViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.AutoMirrored.Filled.Chat, "Servers") }, label = { Text("Servers") })
                NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Default.Mail, "DMs") }, label = { Text("DMs") })
                NavigationBarItem(tab == 2, { tab = 2 }, icon = { Icon(Icons.Default.Group, "Friends") }, label = { Text("Friends") })
                NavigationBarItem(tab == 3, { tab = 3 }, icon = { Icon(Icons.Default.Settings, "Settings") }, label = { Text("Settings") })
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> ServersScreen(
                    onOpenChannel = { id, name -> onOpenChannel(id, name, "server") },
                    onOpenMembers = onOpenMembers,
                )
                1 -> DMListScreen(onOpenDm = { id, name -> onOpenChannel(id, name, "dm") })
                2 -> FriendsScreen(onOpenProfile = onOpenProfile)
                else -> SettingsScreen(onOpenBlockedUsers = onOpenBlockedUsers)
            }
        }
    }
}
