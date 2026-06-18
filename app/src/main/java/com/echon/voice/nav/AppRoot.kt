package com.echon.voice.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echon.voice.feature.auth.AuthStore
import com.echon.voice.feature.auth.EulaScreen
import com.echon.voice.feature.auth.LoginScreen
import com.echon.voice.feature.auth.RegisterScreen
import com.echon.voice.feature.home.HomePlaceholderScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    authStore: AuthStore,
) : ViewModel() {
    val phase = authStore.phase

    init {
        // One-shot session restore + /v1/me at app start.
        viewModelScope.launch { authStore.bootstrap() }
    }
}

/**
 * Top-level router, mirroring the iOS `RootView` phase machine:
 * loading → (signedOut | needsEULA | signedIn).
 */
@Composable
fun AppRoot(
    modifier: Modifier = Modifier,
    viewModel: RootViewModel = hiltViewModel(),
) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        AnimatedContent(
            targetState = phase,
            transitionSpec = { fadeThrough() },
            label = "auth-phase",
        ) { current ->
            when (current) {
                AuthStore.Phase.Loading -> LoadingScreen(Modifier.padding(padding))
                AuthStore.Phase.SignedOut -> AuthFlow(Modifier.padding(padding))
                AuthStore.Phase.NeedsEula -> EulaScreen(modifier = Modifier.padding(padding))
                AuthStore.Phase.SignedIn -> HomePlaceholderScreen(modifier = Modifier.padding(padding))
            }
        }
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

private fun fadeThrough() =
    androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut()
