package com.echon.voice.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echon.voice.core.network.ApiException
import com.echon.voice.model.EmailLoginChallenge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel internal constructor(
    private val logIn: suspend (String, String) -> EmailLoginChallenge?,
    private val verifyLogin: suspend (String, String) -> Unit,
) : ViewModel() {
    @Inject constructor(authStore: AuthStore) : this(authStore::logIn, authStore::verifyLogin)

    // Credentials and challenges deliberately stay in memory, never SavedStateHandle.
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var code by mutableStateOf("")
    var challenge by mutableStateOf<EmailLoginChallenge?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    private var loginEmail = ""
    private var loginPassword = ""

    val verificationEmail: String get() = loginEmail
    val canSubmit: Boolean
        get() = !isLoading && if (challenge != null) code.isNotBlank()
            else email.isNotBlank() && password.isNotBlank()

    fun submit() {
        if (!canSubmit) return
        val pending = challenge
        if (pending == null) {
            loginEmail = email.trim()
            loginPassword = password
            requestCode(resending = false)
            return
        }
        val submittedCode = code.trim()
        isLoading = true
        error = null
        notice = null
        viewModelScope.launch {
            try {
                verifyLogin(pending.id, submittedCode)
                clearVerification()
                // The root observes AuthStore.phase and navigates away.
            } catch (e: ApiException) {
                error = loginError(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun resendCode() {
        if (isLoading || challenge == null) return
        requestCode(resending = true)
    }

    private fun requestCode(resending: Boolean) {
        val submittedEmail = loginEmail
        val submittedPassword = loginPassword
        isLoading = true
        error = null
        notice = null
        code = ""
        viewModelScope.launch {
            try {
                val next = logIn(submittedEmail, submittedPassword)
                if (next == null) clearVerification()
                else {
                    challenge = next
                    if (resending) notice = "A new code has been sent. Use the latest email."
                }
            } catch (e: ApiException) {
                error = loginError(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun startOver() {
        if (isLoading) return
        clearVerification()
        error = null
        notice = null
    }

    private fun clearVerification() {
        challenge = null
        code = ""
        password = ""
        loginEmail = ""
        loginPassword = ""
    }

    private fun loginError(error: ApiException): String = when {
        error is ApiException.Http && error.status == 400 &&
            (error.code == "invalid_or_expired_code" || error.message == "invalid_or_expired_code") ->
            "That code is incorrect or has expired. Try again or request a new code."
        error is ApiException.Http && error.status == 429 ->
            "Too many attempts. Please wait before trying again."
        else -> error.message ?: "Couldn't sign in. Please try again."
    }
}

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val verifying = viewModel.challenge != null
    val codeFocus = remember { FocusRequester() }
    LaunchedEffect(viewModel.challenge?.id) {
        if (verifying) codeFocus.requestFocus()
    }
    BackHandler(enabled = verifying) { viewModel.startOver() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(if (verifying) "Check your email" else "Welcome back", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (verifying) "Enter the sign-in code sent to ${viewModel.verificationEmail}."
            else "Sign in to Echon",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )

        if (verifying) {
            OutlinedTextField(
                value = viewModel.code,
                onValueChange = { viewModel.code = it },
                label = { Text("Verification code") },
                singleLine = true,
                enabled = !viewModel.isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(codeFocus),
            )
        } else {
            OutlinedTextField(
                value = viewModel.email,
                onValueChange = { viewModel.email = it },
                label = { Text("Email") },
                singleLine = true,
                enabled = !viewModel.isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.password,
                onValueChange = { viewModel.password = it },
                label = { Text("Password") },
                singleLine = true,
                enabled = !viewModel.isLoading,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }

        viewModel.notice?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }

        viewModel.error?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }

        Button(
            onClick = viewModel::submit,
            enabled = viewModel.canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
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
            Text(if (verifying) "Verify and sign in" else "Sign in")
        }

        if (verifying) {
            TextButton(
                onClick = viewModel::resendCode,
                enabled = !viewModel.isLoading,
            ) { Text("Send a new code") }
            TextButton(
                onClick = viewModel::startOver,
                enabled = !viewModel.isLoading,
            ) { Text("Back to sign in") }
        } else {
            TextButton(
                onClick = { viewModel.startOver(); onNavigateToRegister() },
                enabled = !viewModel.isLoading,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Don't have an account? Create one")
            }
        }
    }
}
