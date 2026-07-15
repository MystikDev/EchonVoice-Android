package com.echon.voice.feature.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echon.voice.core.designsystem.Avatar
import com.echon.voice.core.network.ApiException
import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.apiCall
import com.echon.voice.core.util.readBytesCapped
import com.echon.voice.feature.auth.AuthStore
import com.echon.voice.model.ChangePasswordRequest
import com.echon.voice.model.OutgoingAttachment
import com.echon.voice.model.ProfileUpdateRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

private val DM_OPTIONS = listOf("everyone" to "Everyone", "friends" to "Friends only", "none" to "No one")

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val authStore: AuthStore,
    private val api: EchonApi,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val user get() = authStore.currentUser.value
    var about by mutableStateOf(user?.about ?: "")
    var dmPrivacy by mutableStateOf(user?.allowDmsFrom ?: "everyone")
    var newAvatarUrl by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set

    val avatarUrlPreview: String? get() = newAvatarUrl ?: user?.avatarUrl

    fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            busy = true; status = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytesCapped(com.echon.voice.core.util.UploadLimits.AVATAR_MAX_BYTES)
                    } ?: error("avatar too large or unreadable")
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val part = MultipartBody.Part.createFormData("file", "avatar.${mime.substringAfter('/', "jpg")}", bytes.toRequestBody(mime.toMediaTypeOrNull()))
                    val purpose = "avatar".toRequestBody("text/plain".toMediaTypeOrNull())
                    apiCall { api.upload(part, purpose) }.url
                }
            }.onSuccess { newAvatarUrl = it }.onFailure { status = "Avatar upload failed" }
            busy = false
        }
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            busy = true; status = null
            try {
                val updated = apiCall { api.updateProfile(ProfileUpdateRequest(about = about, allowDmsFrom = dmPrivacy, avatar = newAvatarUrl)) }
                authStore.applyUpdatedUser(updated)
                onDone()
            } catch (e: ApiException) {
                status = e.message
            } finally {
                busy = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(onBack: () -> Unit, viewModel: ProfileEditViewModel = hiltViewModel()) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::uploadAvatar)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit profile") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Avatar(user = viewModel.run { authStoreUserForAvatar() }, size = 64.dp)
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !viewModel.busy) {
                    Text("Change avatar")
                }
            }
            OutlinedTextField(value = viewModel.about, onValueChange = { viewModel.about = it }, label = { Text("About") }, modifier = Modifier.fillMaxWidth())
            Text("Who can DM you", style = MaterialTheme.typography.titleSmall)
            DM_OPTIONS.forEach { (value, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().selectable(viewModel.dmPrivacy == value) { viewModel.dmPrivacy = value }) {
                    RadioButton(selected = viewModel.dmPrivacy == value, onClick = { viewModel.dmPrivacy = value })
                    Text(label)
                }
            }
            viewModel.status?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = { viewModel.save(onBack) }, enabled = !viewModel.busy, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
}

/** Avatar preview helper using the latest upload or current user. */
@Composable
private fun ProfileEditViewModel.authStoreUserForAvatar() =
    com.echon.voice.model.User(id = "self", avatar = avatarUrlPreview)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val api: EchonApi,
) : ViewModel() {
    var current by mutableStateOf("")
    var new by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set

    fun save(onDone: () -> Unit) {
        if (current.isBlank() || new.length < 8) { status = "New password must be at least 8 characters"; return }
        viewModelScope.launch {
            busy = true; status = null
            try {
                apiCall { api.changePassword(ChangePasswordRequest(current, new)) }
                onDone()
            } catch (e: ApiException) {
                status = e.message
            } finally {
                busy = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(onBack: () -> Unit, viewModel: ChangePasswordViewModel = hiltViewModel()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Change password") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(value = viewModel.current, onValueChange = { viewModel.current = it }, label = { Text("Current password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = viewModel.new, onValueChange = { viewModel.new = it }, label = { Text("New password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            viewModel.status?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = { viewModel.save(onBack) }, enabled = !viewModel.busy, modifier = Modifier.fillMaxWidth()) { Text("Update password") }
        }
    }
}
