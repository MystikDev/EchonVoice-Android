package com.echon.voice.feature.update

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checker: UpdateChecker,
    private val installer: ApkInstaller,
) : ViewModel() {
    var status by mutableStateOf<UpdateStatus>(UpdateStatus.UpToDate)
        private set
    var downloading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var dismissed by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch { status = checker.check() }
    }

    fun update() {
        val available = status as? UpdateStatus.Available ?: return
        if (!installer.canInstall()) {
            // Bounce to settings to grant "install unknown apps", then retry on return.
            installer.requestInstallPermission()
            return
        }
        viewModelScope.launch {
            downloading = true
            error = null
            try {
                val apk = installer.download(available.release.apkUrl)
                installer.launchInstall(apk)
            } catch (e: Exception) {
                error = "Update failed to download. Please try again."
            } finally {
                downloading = false
            }
        }
    }

    fun dismiss() {
        dismissed = true
    }
}

/**
 * Overlay shown when the hosted manifest advertises a newer build. Mandatory
 * updates (below minSupportedVersionCode) can't be dismissed.
 */
@Composable
fun UpdatePrompt(viewModel: UpdateViewModel = hiltViewModel()) {
    val status = viewModel.status
    if (status !is UpdateStatus.Available) return
    if (viewModel.dismissed && !status.mandatory) return

    AlertDialog(
        onDismissRequest = { if (!status.mandatory) viewModel.dismiss() },
        title = { Text("Update available") },
        text = {
            Text(
                buildString {
                    append("Version ${status.release.versionName} is available.")
                    status.release.notes?.let { append("\n\n$it") }
                    viewModel.error?.let { append("\n\n$it") }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = viewModel::update, enabled = !viewModel.downloading) {
                if (viewModel.downloading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(if (status.mandatory) "Update now" else "Update")
            }
        },
        dismissButton = {
            if (!status.mandatory) {
                TextButton(onClick = viewModel::dismiss, enabled = !viewModel.downloading) {
                    Text("Later")
                }
            }
        },
    )
}
