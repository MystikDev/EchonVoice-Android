package com.echon.voice.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echon.voice.feature.auth.AuthStore
import com.echon.voice.feature.auth.EulaScreen
import com.echon.voice.feature.auth.LoginScreen
import com.echon.voice.feature.auth.RegisterScreen
import com.echon.voice.core.updateapi.UpdateGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val authStore: AuthStore,
) : ViewModel() {
    val phase = authStore.phase
    fun recoverStorage(forget: Boolean) { viewModelScope.launch { authStore.recoverStorage(forget) } }

    init {
        // One-shot session restore + /v1/me at app start.
        viewModelScope.launch { authStore.bootstrap() }
    }
}

/**
 * Top-level router, mirroring the iOS `RootView` phase machine:
 * loading → (signedOut | needsEULA | signedIn). Signed-out/EULA screens get
 * safe-drawing insets; the signed-in area manages its own via per-screen Scaffolds.
 */
@Composable
fun AppRoot(
    modifier: Modifier = Modifier,
    viewModel: RootViewModel = hiltViewModel(),
) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = phase,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "auth-phase",
        ) { current ->
            when (current) {
                AuthStore.Phase.Loading -> LoadingScreen()
                AuthStore.Phase.StorageUnavailable -> StorageRecoveryScreen(viewModel::recoverStorage)
                AuthStore.Phase.SignedOut -> AuthFlow(Modifier.safeDrawingPadding())
                AuthStore.Phase.NeedsEula -> EulaScreen(modifier = Modifier.safeDrawingPadding())
                AuthStore.Phase.SignedIn -> SignedInNavHost()
            }
        }

        // Update prompt overlays any phase on the direct-download flavor; no-op on Play.
        UpdateGate()
    }
}

@Composable
private fun AuthFlow(modifier: Modifier = Modifier) {
    var showRegister by rememberSaveable { mutableStateOf(false) }
    if (showRegister) {
        RegisterScreen(onNavigateToLogin = { showRegister = false }, modifier = modifier)
    } else {
        LoginScreen(onNavigateToRegister = { showRegister = true }, modifier = modifier)
    }
}

@Composable
private fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun StorageRecoveryScreen(recover: (Boolean) -> Unit) {
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Saved sign-in is unavailable", style = MaterialTheme.typography.titleLarge)
        Text("Unlock your phone, check your connection, and try again. Your saved sign-in has been kept. You can also forget it and sign in again.")
        Button(onClick = { recover(false) }) { Text("Try again") }
        TextButton(onClick = { confirmForget = true }) { Text("Forget saved sign-in") }
    }
    if (confirmForget) AlertDialog(
        onDismissRequest = { confirmForget = false },
        title = { Text("Forget saved sign-in?") },
        text = { Text("This removes your saved sign-in from this phone. You will need to sign in again.") },
        confirmButton = { TextButton(onClick = { confirmForget = false; recover(true) }) { Text("Forget") } },
        dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel") } },
    )
}
