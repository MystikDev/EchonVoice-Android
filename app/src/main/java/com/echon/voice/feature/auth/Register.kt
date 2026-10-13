package com.echon.voice.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echon.voice.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel internal constructor(
    private val registerAccount: suspend (String, String, String, String, String?) -> Unit,
    private val api: com.echon.voice.core.network.EchonApi,
) : ViewModel() {
    @Inject constructor(authStore: AuthStore, api: com.echon.voice.core.network.EchonApi) : this(authStore::register, api)

    var email by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")

    /** YYYY-MM-DD, required by the backend. */
    var dateOfBirth by mutableStateOf<String?>(null)
        private set

    var tosAccepted by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var captchaConfig by mutableStateOf<com.echon.voice.model.CaptchaConfig?>(null)
        private set
    var captchaLoading by mutableStateOf(false)
        private set
    var captchaError by mutableStateOf<String?>(null)
        private set
    var captchaToken by mutableStateOf<String?>(null)
        private set
    var captchaRevision by mutableStateOf(0)
        private set

    init { loadCaptcha() }

    fun loadCaptcha() {
        if (captchaLoading || isLoading) return
        captchaLoading = true
        captchaError = null
        resetCaptcha()
        viewModelScope.launch {
            try {
                val config = com.echon.voice.core.network.apiCall { api.captchaConfig() }
                require(config.siteKey == null || (config.provider == "turnstile" && validSiteKey(config.siteKey)))
                require(!config.required || config.siteKey != null)
                captchaConfig = config
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) {
                captchaConfig = null
                captchaError = "Couldn't load the signup check. Please retry."
            } finally { captchaLoading = false }
        }
    }

    fun onCaptchaToken(revision: Int, token: String) {
        if (revision != captchaRevision || isLoading) return
        captchaToken = token.takeIf { it.isNotBlank() && it.length <= 2048 }
        if (captchaToken != null) captchaError = null
    }

    fun onCaptchaError(revision: Int) {
        if (revision != captchaRevision || isLoading) return
        captchaToken = null
        captchaError = "Couldn't complete the check. Please retry. If this continues, update Android System WebView."
    }

    fun resetCaptcha() {
        captchaToken = null
        captchaRevision++ // Old WebViews cannot supply a token for the next attempt.
        captchaError = null
    }

    fun setDateOfBirthMillis(utcMillis: Long) {
        dateOfBirth = Instant.ofEpochMilli(utcMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    val canSubmit: Boolean
        get() = email.isNotBlank() &&
            username.isNotBlank() &&
            password.length >= 8 &&
            dateOfBirth != null &&
            tosAccepted &&
            !isLoading && !captchaLoading && captchaConfig != null &&
            (captchaConfig?.siteKey == null || captchaConfig?.required == false || captchaToken != null)

    fun submit() {
        if (!canSubmit) return
        val token = captchaToken
        isLoading = true
        viewModelScope.launch {
            error = null
            try {
                registerAccount(email.trim(), username.trim(), password, dateOfBirth!!, token)
            } catch (e: ApiException) {
                val code = (e as? ApiException.Http)?.code ?: e.message
                error = when (code) {
                    "captcha_failed" -> "Please try the check again."
                    "captcha_required" -> "Please complete the check before creating your account."
                    else -> e.message
                }
                if (code == "captcha_required") {
                    captchaConfig = null // Refresh config if enforcement changed while this form was open.
                }
            } finally {
                resetCaptcha()
                isLoading = false
                if (captchaConfig == null) loadCaptcha()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Create your account", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Join Echon",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )

        OutlinedTextField(
            value = viewModel.email,
            onValueChange = { viewModel.email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = viewModel.username,
            onValueChange = { viewModel.username = it },
            label = { Text("Username") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
        OutlinedTextField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            label = { Text("Password") },
            supportingText = { Text("At least 8 characters") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )

        OutlinedTextField(
            value = viewModel.dateOfBirth ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text("Date of birth") },
            placeholder = { Text("YYYY-MM-DD") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clickable { showDatePicker = true },
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Checkbox(
                checked = viewModel.tosAccepted,
                onCheckedChange = { viewModel.tosAccepted = it },
            )
            Text(
                "I agree to the Terms of Service and Privacy Policy",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (viewModel.captchaLoading) {
            Text("Loading signup check…", modifier = Modifier.padding(top = 12.dp))
        }
        viewModel.captchaConfig?.siteKey?.let { siteKey ->
            val revision = viewModel.captchaRevision
            key(siteKey, revision) {
                TurnstileWidget(
                    siteKey = siteKey,
                    onToken = { viewModel.onCaptchaToken(revision, it) },
                    onError = { viewModel.onCaptchaError(revision) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }
        viewModel.captchaError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            TextButton(onClick = {
                if (viewModel.captchaConfig == null) viewModel.loadCaptcha() else viewModel.resetCaptcha()
            }, enabled = !viewModel.isLoading) { Text("Retry signup check") }
        }

        viewModel.error?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        Button(
            onClick = viewModel::submit,
            enabled = viewModel.canSubmit,
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
            Text("Create account")
        }

        TextButton(
            onClick = onNavigateToLogin,
            enabled = !viewModel.isLoading,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Already have an account? Sign in")
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let(viewModel::setDateOfBirthMillis)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
