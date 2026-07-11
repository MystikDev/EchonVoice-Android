package com.echon.voice.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
    private val appPreferences: com.echon.voice.core.storage.AppPreferences,
) : ViewModel() {
    val currentUser = authStore.currentUser

    val skinEnabled = appPreferences.skinEnabled
    val paletteId = appPreferences.paletteId
    fun setSkinEnabled(enabled: Boolean) = appPreferences.setSkinEnabled(enabled)
    fun setPalette(id: String) = appPreferences.setPalette(id)

    val versionLabel: String = "${com.echon.voice.BuildConfig.VERSION_NAME} (${com.echon.voice.BuildConfig.VERSION_CODE})"

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

            AppearanceSection(viewModel)
            HorizontalDivider()

            SettingsRow(
                title = "Delete account",
                destructive = true,
                onClick = { showDeleteDialog = true },
            )
            HorizontalDivider()

            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
            ) {
                Text("Version", modifier = Modifier.weight(1f))
                Text(viewModel.versionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
private fun AppearanceSection(viewModel: SettingsViewModel) {
    val skinOn by viewModel.skinEnabled.collectAsStateWithLifecycle()
    val paletteId by viewModel.paletteId.collectAsStateWithLifecycle()

    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.setSkinEnabled(!skinOn) }
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("8-bit skin")
            Text(
                "Retro pixel theme",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Switch(
            checked = skinOn,
            onCheckedChange = { viewModel.setSkinEnabled(it) },
        )
    }

    if (skinOn) {
        Text(
            "Palette",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 8.dp),
        )
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        ) {
            com.echon.voice.core.designsystem.EchonPalettes.all.forEach { palette ->
                PaletteSwatch(
                    palette = palette,
                    selected = palette.id == paletteId,
                    onClick = { viewModel.setPalette(palette.id) },
                )
            }
        }
    }
}

@Composable
private fun PaletteSwatch(
    palette: com.echon.voice.core.designsystem.EchonPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(56.dp).clickable(onClick = onClick),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(48.dp)
                .swatchBorder(selected)
                .background(palette.background),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(22.dp).background(palette.primary),
            )
        }
        Text(
            palette.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun Modifier.swatchBorder(selected: Boolean): Modifier = border(
    width = if (selected) 3.dp else 1.dp,
    color = if (selected) androidx.compose.ui.graphics.Color(0xFFFFFFFF) else androidx.compose.ui.graphics.Color(0x33FFFFFF),
)

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
