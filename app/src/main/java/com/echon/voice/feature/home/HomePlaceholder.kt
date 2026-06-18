package com.echon.voice.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echon.voice.feature.auth.AuthStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authStore: AuthStore,
) : ViewModel() {
    val currentUser = authStore.currentUser
    fun signOut() {
        viewModelScope.launch { authStore.signOut() }
    }
}

/**
 * Phase 1 signed-in placeholder — proves the full auth → bootstrap → signed-in
 * round trip and sign-out. The real tabbed main UI (servers/DMs/friends/settings)
 * lands in Phase 3+.
 */
@Composable
fun HomePlaceholderScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val user by viewModel.currentUser.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Signed in", style = MaterialTheme.typography.headlineSmall)
        Text(
            user?.displayHandle ?: "…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        OutlinedButton(
            onClick = viewModel::signOut,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text("Sign out")
        }
    }
}
