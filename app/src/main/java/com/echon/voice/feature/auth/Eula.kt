package com.echon.voice.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echon.voice.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EulaViewModel @Inject constructor(
    private val authStore: AuthStore,
) : ViewModel() {
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun accept() {
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                authStore.acceptTos()
            } catch (e: ApiException) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }

    fun logOut() {
        viewModelScope.launch { authStore.signOut() }
    }
}

/**
 * Native Terms gate. Reached when /v1/me reports the user has not accepted the
 * ToS, gating BOTH freshly-registered and returning users before any access to
 * UGC surfaces — a Play UGC-policy and store-review requirement (the iOS twin
 * was rejected repeatedly over moderation). Mirrors the iOS `EULAView`.
 */
@Composable
fun EulaScreen(
    modifier: Modifier = Modifier,
    viewModel: EulaViewModel = hiltViewModel(),
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "We've updated our Terms",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            "Before you continue, please review and agree to the Echon Terms of Service and Privacy Policy.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            Text(
                "In short: Echon has zero tolerance for objectionable content or abusive behavior. " +
                    "You can report any message or user and block anyone from within the app; reports are " +
                    "reviewed and acted on within 24 hours, and accounts that violate the Terms may be " +
                    "suspended or terminated without notice.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            TextButton(onClick = { uriHandler.openUri("https://echon-voice.com/terms") }) {
                Text("Terms of Service")
            }
            TextButton(onClick = { uriHandler.openUri("https://echon-voice.com/privacy") }) {
                Text("Privacy Policy")
            }
        }

        viewModel.error?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Button(
            onClick = viewModel::accept,
            enabled = !viewModel.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text("Agree & Continue")
        }

        TextButton(
            onClick = viewModel::logOut,
            enabled = !viewModel.isLoading,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text("Log out instead")
        }
    }
}
