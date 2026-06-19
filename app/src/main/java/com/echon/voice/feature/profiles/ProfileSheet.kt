package com.echon.voice.feature.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import com.echon.voice.core.designsystem.Avatar
import com.echon.voice.feature.dms.DMsStore
import com.echon.voice.feature.friends.FriendsStore
import com.echon.voice.feature.moderation.BlocksStore
import com.echon.voice.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val dms: DMsStore,
    private val friends: FriendsStore,
    private val blocks: BlocksStore,
) : ViewModel() {
    var status by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    fun isBlocked(user: User) = blocks.isBlocked(user.id)

    suspend fun openDm(user: User): Pair<String, String>? = runCatching {
        val convo = dms.open(user.id)
        convo.id to (user.username ?: "DM")
    }.getOrNull()

    fun addFriend(user: User) = act("Friend request sent") { friends.sendRequest(user.displayHandle) }
    fun block(user: User) = act("Blocked ${user.username}") { blocks.block(user) }
    fun unblock(user: User) = act("Unblocked") { blocks.unblock(user.id) }

    private fun act(success: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            status = null
            status = runCatching { block() }.fold({ success }, { it.message ?: "Failed" })
            busy = false
        }
    }
}

/** Profile actions: message, add friend, report, block/unblock. Reused from
 *  message author taps, friends, and member lists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileSheet(
    user: User,
    onOpenDm: (channelId: String, name: String) -> Unit,
    onReport: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val blocked = viewModel.isBlocked(user)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Avatar(user = user, size = 72.dp)
            Text(user.username ?: "Unknown", style = MaterialTheme.typography.headlineSmall)
            Text(user.displayHandle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            user.about?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
            viewModel.status?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }

            Button(
                onClick = {
                    scope.launch { viewModel.openDm(user)?.let { (id, name) -> onOpenDm(id, name); onDismiss() } }
                },
                enabled = !viewModel.busy && !blocked,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Message") }

            OutlinedButton(
                onClick = { viewModel.addFriend(user) },
                enabled = !viewModel.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add friend") }

            TextButton(onClick = onReport, modifier = Modifier.fillMaxWidth()) {
                Text("Report", color = MaterialTheme.colorScheme.error)
            }
            TextButton(
                onClick = { if (blocked) viewModel.unblock(user) else viewModel.block(user) },
                enabled = !viewModel.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (blocked) "Unblock" else "Block", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
