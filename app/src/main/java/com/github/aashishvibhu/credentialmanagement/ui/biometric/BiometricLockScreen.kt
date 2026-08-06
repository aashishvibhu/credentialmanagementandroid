package com.github.aashishvibhu.credentialmanagement.ui.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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

@Composable
fun BiometricLockScreen(
    onAuthenticated: () -> Unit,
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

    // Skip biometric if hardware not available (e.g. some emulators)
    if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
        LaunchedEffect(Unit) { authenticated() }
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
            .setTitle("Unlock Credential Manager")
            .setSubtitle("Authenticate to access your vault")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()

        prompt = BiometricPrompt(activity, executor, callback)
        prompt!!.authenticate(promptInfo)
    }

    LaunchedEffect(Unit) { launchPrompt() }

    DisposableEffect(Unit) {
        onDispose { prompt?.cancelAuthentication() }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            Text("Credential Manager", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Authentication required",
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
                Button(onClick = { showRetry = false; errorMessage = ""; launchPrompt() }) {
                    Text("Try Again")
                }
            }
        }
    }
}
