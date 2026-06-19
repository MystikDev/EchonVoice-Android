package com.echon.voice.feature.invites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.apiCall
import com.echon.voice.feature.servers.ServersStore
import com.echon.voice.model.InvitePreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InviteViewModel @Inject constructor(
    private val api: EchonApi,
    private val servers: ServersStore,
) : ViewModel() {
    var preview by mutableStateOf<InvitePreview?>(null)
        private set
    var generatedCode by mutableStateOf<String?>(null)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    fun preview(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch {
            busy = true; status = null
            runCatching { apiCall { api.previewInvite(code.trim()) } }
                .onSuccess { preview = it }
                .onFailure { status = "Invalid or expired code" }
            busy = false
        }
    }

    fun join(code: String, onDone: () -> Unit) {
        viewModelScope.launch {
            busy = true; status = null
            runCatching {
                apiCall { api.useInvite(code.trim()) }
                servers.loadServers()
            }.onSuccess { onDone() }.onFailure { status = it.message ?: "Failed to join" }
            busy = false
        }
    }

    fun createInvite(channelId: String) {
        viewModelScope.launch {
            busy = true; status = null
            runCatching { apiCall { api.createInvite(channelId) }.code }
                .onSuccess { generatedCode = it }
                .onFailure { status = "Could not create invite" }
            busy = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinServerSheet(onDismiss: () -> Unit, viewModel: InviteViewModel = hiltViewModel()) {
    var code by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Join a server", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = code, onValueChange = { code = it; viewModel.preview(it) }, label = { Text("Invite code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            viewModel.preview?.let { p ->
                Text("Server: ${p.server?.name ?: "Unknown"}", style = MaterialTheme.typography.bodyMedium)
                p.server?.memberCount?.let { Text("$it members", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            viewModel.status?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = { viewModel.join(code) { onDismiss() } }, enabled = code.isNotBlank() && !viewModel.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Join")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInviteSheet(channelId: String, onDismiss: () -> Unit, viewModel: InviteViewModel = hiltViewModel()) {
    val clipboard = LocalClipboardManager.current
    androidx.compose.runtime.LaunchedEffect(channelId) { viewModel.createInvite(channelId) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Invite to server", style = MaterialTheme.typography.titleLarge)
            val code = viewModel.generatedCode
            if (code != null) {
                Text(code, style = MaterialTheme.typography.headlineSmall)
                Button(onClick = { clipboard.setText(AnnotatedString(code)) }, modifier = Modifier.fillMaxWidth()) { Text("Copy code") }
            } else {
                Text(viewModel.status ?: "Generating…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
