package com.github.aashishvibhu.credentialmanagement.ui.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.aashishvibhu.credentialmanagement.ui.components.AppMark

@Composable
fun BiometricLockScreen(
    onAuthenticated: () -> Unit,
    onUseMasterPassword: () -> Unit,
    viewModel: LockViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var showRetry by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf<BiometricPrompt?>(null) }

    // Clears the lock state (and triggers the in-memory wipe to refill) before navigating.
    fun authenticated() {
        viewModel.unlock()
        onAuthenticated()
    }

    val biometricManager = BiometricManager.from(context)
    val canAuthenticate = biometricManager.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)

    // No usable biometric/screen lock, or turned off in Settings: ask for the master password instead
    if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS || !viewModel.biometricEnabled) {
        LaunchedEffect(Unit) { onUseMasterPassword() }
        return
    }

    fun launchPrompt() {
        val activity = context as? FragmentActivity ?: return
        val executor = ContextCompat.getMainExecutor(context)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authenticated()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    errorMessage = errString.toString()
                }
                showRetry = true
            }
            override fun onAuthenticationFailed() {
                showRetry = true
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Vault")
            .setSubtitle("Confirm it's you to see your passwords")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()

        prompt = BiometricPrompt(activity, executor, callback)
        prompt!!.authenticate(promptInfo)
    }

    LaunchedEffect(Unit) { launchPrompt() }

    DisposableEffect(Unit) {
        onDispose { prompt?.cancelAuthentication() }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppMark()
            Spacer(Modifier.height(20.dp))
            Text("Vault is locked", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Use your fingerprint, face or screen lock to open it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (errorMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
            if (showRetry) {
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { showRetry = false; errorMessage = ""; launchPrompt() },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
                ) {
                    Text("Unlock")
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { prompt?.cancelAuthentication(); onUseMasterPassword() }) {
                Text("Use master password", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
