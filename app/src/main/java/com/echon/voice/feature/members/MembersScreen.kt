package com.echon.voice.feature.members

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.echon.voice.core.realtime.PresenceStore
import com.echon.voice.core.realtime.PresenceState
import com.echon.voice.core.realtime.PresenceStatus
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.echon.voice.core.designsystem.AvatarWithPresence
import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.apiCall
import com.echon.voice.model.Member
import com.echon.voice.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MembersViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val api: EchonApi,
    private val presenceStore: PresenceStore,
) : ViewModel() {
    val serverId: String = checkNotNull(savedState["serverId"])
    var members by mutableStateOf<List<Member>>(emptyList())
        private set

    val presence = presenceStore.servers
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun observe() = coroutineScope {
        launch { presenceStore.watchServer(serverId) }
        while (isActive) {
            try {
                members = apiCall { api.serverMembers(serverId) }.members
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Could not refresh members. Retrying…"
            } finally {
                loading = false
            }
            delay(30_000)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    onBack: () -> Unit,
    onOpenProfile: (User) -> Unit,
    viewModel: MembersViewModel = hiltViewModel(),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.observe() }
    }
    val presenceByServer by viewModel.presence.collectAsStateWithLifecycle()
    val presence = presenceByServer[viewModel.serverId] ?: PresenceState()
    val groups = groupMembers(viewModel.members, presence)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Members") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (viewModel.loading) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
            viewModel.error?.let { message -> item { Text(message, Modifier.padding(16.dp)) } }
            if (!presence.loaded) item {
                Text(
                    if (presence.failed) "Presence unavailable. Retrying…" else "Waiting for current presence…",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!viewModel.loading && viewModel.error == null && viewModel.members.isEmpty()) {
                item { Text("No members", Modifier.padding(16.dp)) }
            }
            groups.forEach { group ->
                item(key = "group-${group.label}") {
                    Text("${group.label} — ${group.members.size}", Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleSmall)
                }
                items(group.members, key = { it.user.id }) { member ->
                    val status = presence.status(member.user.id)
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onOpenProfile(member.user) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AvatarWithPresence(user = member.user, status = status, size = 40.dp)
                        Column {
                            Text(member.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text("${member.user.displayHandle} · ${PresenceStatus.label(status)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

internal data class MemberPresenceGroup(val label: String, val members: List<Member>)

internal fun groupMembers(members: List<Member>, presence: PresenceState): List<MemberPresenceGroup> {
    val sorted = members.distinctBy { it.user.id }.sortedWith(
        compareBy<Member> { it.displayName.lowercase(java.util.Locale.ROOT) }.thenBy { it.user.id }
    )
    return listOf(
        MemberPresenceGroup("Online", sorted.filter { presence.status(it.user.id) in setOf("online", "idle", "dnd") }),
        MemberPresenceGroup("Offline", sorted.filter { presence.status(it.user.id) == "offline" }),
        MemberPresenceGroup("Status unavailable", sorted.filter { presence.status(it.user.id) == null }),
    ).filter { it.members.isNotEmpty() }
}
