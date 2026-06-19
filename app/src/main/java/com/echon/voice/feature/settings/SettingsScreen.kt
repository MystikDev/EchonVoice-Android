package com.echon.voice.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echon.voice.core.network.ApiException
import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.apiCall
import com.echon.voice.feature.auth.AuthStore
import com.echon.voice.model.DeleteAccountRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authStore: AuthStore,
    private val api: EchonApi,
) : ViewModel() {
    val currentUser = authStore.currentUser

    var deletePassword by mutableStateOf("")
    var deleteError by mutableStateOf<String?>(null)
        private set
    var deleting by mutableStateOf(false)
        private set

    fun signOut() {
        viewModelScope.launch { authStore.signOut() }
    }

    /** Deletes the account (password-confirmed) then signs out. */
    fun deleteAccount() {
        viewModelScope.launch {
            deleting = true
            deleteError = null
            try {
                apiCall { api.deleteAccount(DeleteAccountRequest(currentPassword = deletePassword)) }
                authStore.signOut()
            } catch (e: ApiException) {
                deleteError = e.message
            } finally {
                deleting = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenBlockedUsers: () -> Unit,
    onOpenEditProfile: () -> Unit = {},
    onOpenChangePassword: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    user?.username ?: "…",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    user?.displayHandle ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()

            SettingsRow(title = "Edit profile", onClick = onOpenEditProfile)
            HorizontalDivider()
            SettingsRow(title = "Change password", onClick = onOpenChangePassword)
            HorizontalDivider()
            SettingsRow(title = "Blocked users", onClick = onOpenBlockedUsers)
            HorizontalDivider()

            SettingsRow(
                title = "Delete account",
                destructive = true,
                onClick = { showDeleteDialog = true },
            )
            HorizontalDivider()

            OutlinedButton(
                onClick = viewModel::signOut,
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
            ) {
                Text("Sign out")
            }
        }
    }

    if (showDeleteDialog) {
        DeleteAccountDialog(
            viewModel = viewModel,
            onDismiss = {
                showDeleteDialog = false
                viewModel.deletePassword = ""
            },
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
        )
        if (!destructive) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun DeleteAccountDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!viewModel.deleting) onDismiss() },
        title = { Text("Delete your account?") },
        text = {
            Column {
                Text(
                    "This permanently deletes your account, messages, and servers you own. " +
                        "This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = viewModel.deletePassword,
                    onValueChange = { viewModel.deletePassword = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                viewModel.deleteError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::deleteAccount,
                enabled = viewModel.deletePassword.isNotBlank() && !viewModel.deleting,
            ) {
                if (viewModel.deleting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text("Delete forever", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !viewModel.deleting) { Text("Cancel") }
        },
    )
}
