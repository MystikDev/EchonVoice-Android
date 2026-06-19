package com.echon.voice.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
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
import com.echon.voice.core.update.ApkInstaller
import com.echon.voice.core.update.UpdateChecker
import com.echon.voice.core.update.UpdateStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Auto-updater. On launch it checks the manifest and, when a newer build exists,
 * silently downloads and installs it (no taps on Android 12+ for a same-key
 * self-update). The only UI is a one-time request to allow installs if that
 * permission hasn't been granted, and an unobtrusive "updating" indicator.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checker: UpdateChecker,
    private val installer: ApkInstaller,
) : ViewModel() {
    enum class Phase { Idle, Downloading, NeedsPermission }

    var phase by mutableStateOf(Phase.Idle)
        private set

    private var pendingApkUrl: String? = null

    init {
        viewModelScope.launch {
            val status = checker.check()
            if (status is UpdateStatus.Available) autoUpdate(status.release.apkUrl)
        }
    }

    private suspend fun autoUpdate(apkUrl: String) {
        if (!installer.canInstall()) {
            pendingApkUrl = apkUrl
            phase = Phase.NeedsPermission
            return
        }
        phase = Phase.Downloading
        try {
            val apk = installer.download(apkUrl)
            installer.install(apk) // silent on API 31+; system installer otherwise
        } catch (e: Exception) {
            android.util.Log.w("EchonUpdate", "auto-update failed", e)
        } finally {
            phase = Phase.Idle
        }
    }

    fun grantPermission() {
        phase = Phase.Idle
        installer.requestInstallPermission() // one-time; auto-update proceeds next launch
    }

    fun dismissPermission() {
        phase = Phase.Idle
    }
}

@Composable
fun UpdatePrompt(viewModel: UpdateViewModel = hiltViewModel()) {
    when (viewModel.phase) {
        UpdateViewModel.Phase.Downloading -> UpdatingIndicator()
        UpdateViewModel.Phase.NeedsPermission -> AllowInstallDialog(
            onAllow = viewModel::grantPermission,
            onDismiss = viewModel::dismissPermission,
        )
        UpdateViewModel.Phase.Idle -> Unit
    }
}

@Composable
private fun UpdatingIndicator() {
    Snackbar(modifier = Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            Text("Updating Echon…")
        }
    }
}

@Composable
private fun AllowInstallDialog(onAllow: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keep Echon up to date") },
        text = { Text("Allow Echon to install its own updates so it can update automatically. You'll only need to grant this once.") },
        confirmButton = { TextButton(onClick = onAllow) { Text("Allow") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
