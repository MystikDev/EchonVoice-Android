package com.echon.voice.feature.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echon.voice.core.designsystem.Avatar
import com.echon.voice.model.FriendRequest
import com.echon.voice.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friends: FriendsStore,
) : ViewModel() {
    val friendList = friends.friends
    val incoming = friends.incoming
    val outgoing = friends.outgoing

    var handle by mutableStateOf("")
    var status by mutableStateOf<String?>(null)
        private set

    init { refresh() }
    fun refresh() = viewModelScope.launch { runCatching { friends.load() } }

    fun add() {
        if (handle.isBlank()) return
        viewModelScope.launch {
            status = runCatching { friends.sendRequest(handle) }.fold({ handle = ""; "Request sent" }, { it.message ?: "Failed" })
        }
    }

    fun accept(id: String) = viewModelScope.launch { runCatching { friends.accept(id) } }
    fun decline(id: String) = viewModelScope.launch { runCatching { friends.decline(id) } }
    fun remove(userId: String) = viewModelScope.launch { runCatching { friends.remove(userId) } }
}

@Composable
fun FriendsScreen(
    onOpenProfile: (User) -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val friends by viewModel.friendList.collectAsStateWithLifecycle()
    val incoming by viewModel.incoming.collectAsStateWithLifecycle()
    val outgoing by viewModel.outgoing.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Add friend", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = viewModel.handle,
                onValueChange = { viewModel.handle = it },
                placeholder = { Text("username#1234") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = viewModel::add) { Text("Add") }
        }
        viewModel.status?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp)) }

        if (incoming.isNotEmpty()) {
            SectionHeader("Incoming requests")
            incoming.forEach { req ->
                RequestRow(req.sender, "Accept", "Decline", { viewModel.accept(req.id) }, { viewModel.decline(req.id) })
            }
        }
        if (outgoing.isNotEmpty()) {
            SectionHeader("Sent requests")
            outgoing.forEach { req ->
                RequestRow(req.receiver, null, "Cancel", {}, { viewModel.decline(req.id) })
            }
        }

        SectionHeader("Friends")
        if (friends.isEmpty()) {
            Text("No friends yet", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            friends.forEach { friend ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProfile(friend) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Avatar(user = friend, size = 36.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(friend.username ?: "Unknown")
                        Text(friend.displayHandle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun RequestRow(user: User?, accept: String?, reject: String, onAccept: () -> Unit, onReject: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Avatar(user = user, size = 36.dp)
        Text(user?.username ?: "Unknown", modifier = Modifier.weight(1f))
        if (accept != null) Button(onClick = onAccept) { Text(accept) }
        OutlinedButton(onClick = onReject) { Text(reject) }
    }
}
