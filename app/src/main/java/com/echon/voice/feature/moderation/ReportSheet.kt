package com.echon.voice.feature.moderation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.echon.voice.core.network.ApiException
import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.apiCall
import com.echon.voice.model.ReportReason
import com.echon.voice.model.ReportRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What a report targets — a message or a user. */
sealed interface ReportTarget {
    data class Message(val id: String) : ReportTarget
    data class UserTarget(val id: String, val name: String) : ReportTarget
}

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val api: EchonApi,
) : ViewModel() {
    var selectedReason by mutableStateOf<ReportReason?>(null)
    var description by mutableStateOf("")
    var isSubmitting by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun submit(target: ReportTarget, onDone: () -> Unit) {
        val reason = selectedReason ?: return
        viewModelScope.launch {
            isSubmitting = true
            error = null
            try {
                val body = ReportRequest(reason.value, description.takeIf { it.isNotBlank() })
                when (target) {
                    is ReportTarget.Message -> apiCall { api.reportMessage(target.id, body) }
                    is ReportTarget.UserTarget -> apiCall { api.reportUser(target.id, body) }
                }
                onDone()
            } catch (e: ApiException) {
                error = e.message
            } finally {
                isSubmitting = false
            }
        }
    }
}

/**
 * Bottom sheet to report a message or user. Reusable from message actions and
 * profile screens. Submitting forwards the chosen [ReportReason] to the backend.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(
    target: ReportTarget,
    onDismiss: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            val title = when (target) {
                is ReportTarget.Message -> "Report message"
                is ReportTarget.UserTarget -> "Report ${target.name}"
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                "Reports are reviewed and acted on within 24 hours.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            ReportReason.entries.forEach { reason ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = viewModel.selectedReason == reason,
                            onClick = { viewModel.selectedReason = reason },
                        )
                        .padding(vertical = 4.dp),
                ) {
                    RadioButton(
                        selected = viewModel.selectedReason == reason,
                        onClick = { viewModel.selectedReason = reason },
                    )
                    Text(reason.label, modifier = Modifier.padding(start = 4.dp))
                }
            }

            OutlinedTextField(
                value = viewModel.description,
                onValueChange = { viewModel.description = it },
                label = { Text("Details (optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            viewModel.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Button(
                onClick = { viewModel.submit(target, onDismiss) },
                enabled = viewModel.selectedReason != null && !viewModel.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                if (viewModel.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Text("Submit report")
            }
        }
    }
}
