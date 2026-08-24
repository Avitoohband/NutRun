package com.avitoohband.nutrun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AuthenticationOverviewContent(
    state: AuthenticationUiState,
    onAuthenticate: (String, String, Boolean) -> Unit,
    onSendPasswordReset: (String) -> Unit,
    onSetMode: (AuthenticationMode) -> Unit,
    onClearFeedback: () -> Unit,
    onDemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("NutRun", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            when (state.mode) {
                AuthenticationMode.SIGN_IN -> "Sign in to keep your training and health log together."
                AuthenticationMode.CREATE_ACCOUNT -> "Create an account to start your 30-day ad-free trial."
                AuthenticationMode.RESET_PASSWORD -> "Enter your email and we'll send reset instructions."
            }
        )
        state.message?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("auth-message")
            )
        }
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                onClearFeedback()
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("auth-email"),
            label = {
                Text(
                    if (BuildConfig.DEBUG && state.mode != AuthenticationMode.RESET_PASSWORD) {
                        "Email or demo username"
                    } else {
                        "Email"
                    }
                )
            },
            isError = state.emailError != null,
            supportingText = state.emailError?.let { { Text(it) } },
            singleLine = true,
            enabled = !state.busy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = if (state.mode == AuthenticationMode.RESET_PASSWORD) {
                    ImeAction.Done
                } else {
                    ImeAction.Next
                }
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (state.mode == AuthenticationMode.RESET_PASSWORD) {
                        onSendPasswordReset(email)
                    }
                }
            )
        )
        if (state.mode != AuthenticationMode.RESET_PASSWORD) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    onClearFeedback()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth-password"),
                label = { Text("Password") },
                isError = state.passwordError != null,
                supportingText = state.passwordError?.let { { Text(it) } },
                singleLine = true,
                enabled = !state.busy,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) {
                                "Hide password"
                            } else {
                                "Show password"
                            }
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        when (state.mode) {
                            AuthenticationMode.SIGN_IN -> onAuthenticate(email, password, false)
                            AuthenticationMode.CREATE_ACCOUNT -> onAuthenticate(email, password, true)
                            AuthenticationMode.RESET_PASSWORD -> Unit
                        }
                    }
                )
            )
        }
        Spacer(Modifier.height(16.dp))
        when (state.mode) {
            AuthenticationMode.SIGN_IN -> {
                Button(
                    onClick = { onAuthenticate(email, password, false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth-sign-in"),
                    enabled = !state.busy
                ) {
                    if (state.busy) CircularProgressIndicator(modifier = Modifier.height(18.dp))
                    else Text("Sign in")
                }
                OutlinedButton(
                    onClick = { onSetMode(AuthenticationMode.CREATE_ACCOUNT) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth-create-account"),
                    enabled = !state.busy
                ) {
                    Text("Create account")
                }
                TextButton(
                    onClick = { onSetMode(AuthenticationMode.RESET_PASSWORD) },
                    modifier = Modifier.testTag("auth-forgot-password"),
                    enabled = !state.busy
                ) {
                    Text("Forgot password?")
                }
            }
            AuthenticationMode.CREATE_ACCOUNT -> {
                Button(
                    onClick = { onAuthenticate(email, password, true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth-create-submit"),
                    enabled = !state.busy
                ) {
                    if (state.busy) CircularProgressIndicator(modifier = Modifier.height(18.dp))
                    else Text("Create account")
                }
                TextButton(
                    onClick = { onSetMode(AuthenticationMode.SIGN_IN) },
                    modifier = Modifier.testTag("auth-back-to-sign-in"),
                    enabled = !state.busy
                ) {
                    Text("Back to sign in")
                }
                Text(
                    "A 30-day ad-free trial starts when the account is first created. " +
                        "No payment details required.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.testTag("auth-trial-copy")
                )
            }
            AuthenticationMode.RESET_PASSWORD -> {
                Button(
                    onClick = { onSendPasswordReset(email) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth-send-reset"),
                    enabled = !state.busy
                ) {
                    if (state.busy) CircularProgressIndicator(modifier = Modifier.height(18.dp))
                    else Text("Send reset email")
                }
                TextButton(
                    onClick = { onSetMode(AuthenticationMode.SIGN_IN) },
                    enabled = !state.busy
                ) {
                    Text("Back to sign in")
                }
            }
        }
        if (BuildConfig.DEBUG) {
            OutlinedButton(
                onClick = onDemo,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("demo-login"),
                enabled = !state.busy
            ) {
                Text("Enter demo")
            }
        }
    }
}
