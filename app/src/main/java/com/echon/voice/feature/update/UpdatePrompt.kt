package com.echon.voice.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import com.echon.voice.core.update.UpdateConfig
import com.echon.voice.core.update.UpdateStatus
import com.echon.voice.feature.auth.AuthStore
import com.echon.voice.model.AppRelease
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps Echon up to date with minimal user effort:
 *  - **On launch:** check the manifest and, if newer, silently download + install
 *    (no taps on Android 12+ for a same-key self-update).
 *  - **While signed in:** re-check every 10 minutes; if a new version appears
 *    mid-session, ask "Update now / Later" rather than restarting the app under
 *    the user. "Update now" performs the same silent install.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checker: UpdateChecker,
    private val installer: ApkInstaller,
    private val authStore: AuthStore,
) : ViewModel() {
    enum class Phase { Idle, Downloading, NeedsPermission, PromptAvailable }

    var phase by mutableStateOf(Phase.Idle)
        private set
    var availableVersionName by mutableStateOf<String?>(null)
        private set

    private var pendingRelease: AppRelease? = null

    init {
        // On load: silent auto-update.
        viewModelScope.launch {
            val status = checker.check()
            if (status is UpdateStatus.Available) silentUpdate(status.release)
        }
        // Post-login: poll every 10 minutes and prompt (collectLatest stops the
        // loop automatically on sign-out / phase change).
        viewModelScope.launch {
            authStore.phase.collectLatest { authPhase ->
                if (authPhase != AuthStore.Phase.SignedIn) return@collectLatest
                while (true) {
                    delay(CHECK_INTERVAL_MS)
                    if (phase != Phase.Idle) continue // a download/prompt is already in flight
                    val status = checker.check()
                    if (status is UpdateStatus.Available) {
                        pendingRelease = status.release
                        availableVersionName = status.release.versionName
                        phase = Phase.PromptAvailable
                    }
                }
            }
        }
    }

    /** User chose "Update now" from the mid-session prompt. */
    fun updateNow() {
        val release = pendingRelease ?: return
        viewModelScope.launch { silentUpdate(release) }
    }

    fun updateLater() {
        phase = Phase.Idle // re-prompts at the next 10-minute check
    }

    fun grantPermission() {
        phase = Phase.Idle
        installer.requestInstallPermission() // one-time; auto-update resumes after
    }

    fun dismissPermission() {
        phase = Phase.Idle
    }

    private suspend fun silentUpdate(release: AppRelease) {
        if (!installer.canInstall()) {
            pendingRelease = release
            phase = Phase.NeedsPermission
            return
        }
        phase = Phase.Downloading
        try {
            // Always pull from the fixed, compile-time release URL rather than a
            // manifest-supplied one, so a tampered manifest can't redirect the
            // download; the manifest only decides *whether* to update and carries
            // the expected hash to verify the bytes.
            val apk = installer.download(UpdateConfig.LATEST_APK_URL, expectedSha256 = release.sha256)
            installer.install(apk) // silent on API 31+; system installer otherwise
        } catch (e: Exception) {
            android.util.Log.w("EchonUpdate", "auto-update failed", e)
        } finally {
            phase = Phase.Idle
        }
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 10 * 60 * 1000L // 10 minutes
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
        UpdateViewModel.Phase.PromptAvailable -> UpdateAvailableDialog(
            versionName = viewModel.availableVersionName,
            onUpdate = viewModel::updateNow,
            onLater = viewModel::updateLater,
        )
        UpdateViewModel.Phase.Idle -> Unit
    }
}

@Composable
private fun UpdateAvailableDialog(versionName: String?, onUpdate: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Update available") },
        text = {
            Text(
                "A new version of Echon" + (versionName?.let { " ($it)" } ?: "") +
                    " is ready. Update now? The app will briefly restart.",
            )
        },
        confirmButton = { TextButton(onClick = onUpdate) { Text("Update now") } },
        dismissButton = { TextButton(onClick = onLater) { Text("Later") } },
    )
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
