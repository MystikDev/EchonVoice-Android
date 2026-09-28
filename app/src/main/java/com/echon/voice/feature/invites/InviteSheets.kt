package com.echon.voice.feature.invites

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echon.voice.core.network.ApiException
import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.apiCall
import com.echon.voice.feature.servers.ServersStore
import com.echon.voice.model.Channel
import com.echon.voice.model.ChannelKind
import com.echon.voice.model.InvitePreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InviteViewModel @Inject constructor(
    private val api: EchonApi,
    private val servers: ServersStore,
) : ViewModel() {
    var preview by mutableStateOf<InvitePreview?>(null); private set
    var generatedCode by mutableStateOf<String?>(null); private set
    var generatedChannelId by mutableStateOf<String?>(null); private set
    var status by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set
    private var operation: Job? = null
    private var generation = 0
    private var previewedCode: String? = null

    fun reset() {
        generation++
        operation?.cancel()
        preview = null; previewedCode = null; generatedCode = null; generatedChannelId = null
        status = null; busy = false
    }

    fun preview(input: String) {
        reset()
        val code = inviteCode(input)
        if (code == null) {
            if (input.isNotBlank()) status = "Enter an invite code or an Echon invite link."
            return
        }
        val epoch = generation
        busy = true
        operation = viewModelScope.launch {
            try {
                delay(300)
                val result = apiCall { api.previewInvite(code) }
                if (epoch == generation) { preview = result; previewedCode = code }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (epoch == generation) status = inviteError(e) }
            finally { if (epoch == generation) busy = false }
        }
    }

    fun canJoin(input: String) = !busy && preview != null && previewedCode == inviteCode(input)

    fun join(input: String, onDone: () -> Unit) {
        if (!canJoin(input)) return
        val code = previewedCode ?: return
        val epoch = generation
        busy = true; status = null
        operation = viewModelScope.launch {
            try {
                apiCall { api.useInvite(code) }
                servers.loadServers()
                if (epoch == generation) onDone()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (epoch == generation) status = inviteError(e) }
            finally { if (epoch == generation) busy = false }
        }
    }

    fun createInvite(channelId: String) {
        reset()
        val epoch = generation
        busy = true
        operation = viewModelScope.launch {
            try {
                val result = apiCall { api.createInvite(channelId) }
                check(result.code.isNotBlank())
                if (epoch == generation) { generatedCode = result.code; generatedChannelId = channelId }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (epoch == generation) status = inviteError(e) }
            finally { if (epoch == generation) busy = false }
        }
    }

    fun reloadChannels(serverId: String) {
        if (busy) return
        val epoch = generation
        busy = true; status = null
        operation = viewModelScope.launch {
            try { servers.loadChannels(serverId) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (epoch == generation) status = inviteError(e) }
            finally { if (epoch == generation) busy = false }
        }
    }

    private fun inviteError(error: Exception): String = when (error) {
        is ApiException.Http -> when (error.status) {
            403 -> "You don't have permission to use invites for this channel. Try another channel or ask a server administrator."
            404, 410 -> "This invite or channel is unavailable. Ask for a new invite."
            429 -> "Too many requests. Wait a moment and try again."
            else -> error.message ?: "Invite request failed. Please try again."
        }
        is ApiException.Unauthorized -> "Please sign in again to use invites."
        else -> "Could not reach the invite service. Please try again."
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinServerSheet(onDismiss: () -> Unit, viewModel: InviteViewModel = hiltViewModel(key = "join-invite")) {
    var input by remember { mutableStateOf("") }
    DisposableEffect(viewModel) { viewModel.reset(); onDispose { viewModel.reset() } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Join a server", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = input, onValueChange = { input = it; viewModel.preview(it) }, label = { Text("Invite code or link") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            viewModel.preview?.let { p -> Text("Server: ${p.server?.name ?: "Unknown"}") }
            if (viewModel.busy) Text("Checking invite…")
            viewModel.status?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { viewModel.preview(input) }, enabled = !viewModel.busy) { Text("Retry") }
            }
            Button(onClick = { viewModel.join(input, onDismiss) }, enabled = viewModel.canJoin(input), modifier = Modifier.fillMaxWidth()) { Text("Join") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInviteSheet(serverId: String, channels: List<Channel>, onDismiss: () -> Unit,
                      viewModel: InviteViewModel = hiltViewModel(key = "create-invite-$serverId")) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val available = channels.filter { it.type == ChannelKind.TEXT || it.type == ChannelKind.VOICE }.sortedBy { it.position ?: 0 }
    var selectedId by remember(serverId) { mutableStateOf<String?>(null) }
    val channel = available.firstOrNull { it.id == selectedId } ?: available.firstOrNull { it.type == ChannelKind.TEXT } ?: available.firstOrNull()
    var copied by remember(channel?.id) { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    DisposableEffect(viewModel) { onDispose { viewModel.reset() } }
    LaunchedEffect(channel?.id) { if (channel != null) viewModel.createInvite(channel.id) else viewModel.reset() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Invite to server", style = MaterialTheme.typography.titleLarge)
            if (channel == null) {
                Text("No channels are available yet. Reload channels or ask an administrator to create one.")
                viewModel.status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { viewModel.reloadChannels(serverId) }, enabled = !viewModel.busy) { Text("Reload channels") }
            } else {
                TextButton(onClick = { expanded = true }) { Text("Channel: ${channel.name ?: "channel"} ▾") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    available.forEach { item -> DropdownMenuItem(text = { Text(item.name ?: "channel") }, onClick = { selectedId = item.id; expanded = false }) }
                }
                val code = viewModel.generatedCode.takeIf { viewModel.generatedChannelId == channel.id }
                if (code != null) {
                    Text(code, style = MaterialTheme.typography.headlineSmall)
                    Text("Share this code. The recipient can paste it into Join a server in Echon.")
                    Button(onClick = { clipboard.setText(AnnotatedString(code)); copied = true }, modifier = Modifier.fillMaxWidth()) { Text(if (copied) "Copied" else "Copy code") }
                    OutlinedButton(onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, code)
                        }, "Share Echon invite"))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Share invite") }
                } else {
                    Text(viewModel.status ?: "Generating invite…")
                    if (!viewModel.busy) Button(onClick = { viewModel.createInvite(channel.id) }) { Text("Retry") }
                }
            }
        }
    }
}
