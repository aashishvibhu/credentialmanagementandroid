package com.github.aashishvibhu.credentialmanagement.ui.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Switch
import androidx.compose.runtime.LaunchedEffect
import com.github.aashishvibhu.credentialmanagement.ui.components.PasswordField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.aashishvibhu.credentialmanagement.sync.SyncState
import com.github.aashishvibhu.credentialmanagement.ui.components.SectionCard
import com.github.aashishvibhu.credentialmanagement.ui.components.SettingRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onRecoveryKeyReady: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val syncState       by viewModel.syncState.collectAsState()
    val lastSyncTime    by viewModel.lastSyncTime.collectAsState()
    val signedInEmail   by viewModel.signedInEmail.collectAsState()
    val biometricUnlock by viewModel.biometricUnlock.collectAsState()
    val context = LocalContext.current
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.recoveryReady.collect {
            showRecoveryDialog = false
            onRecoveryKeyReady()
        }
    }

    if (showRecoveryDialog) {
        NewRecoveryKeyDialog(
            viewModel = viewModel,
            onDismiss = { showRecoveryDialog = false; viewModel.clearRecoveryError() }
        )
    }

    val appVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
        } catch (_: PackageManager.NameNotFoundException) { "—" }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out?") },
            text = { Text("Your vault stays encrypted in Google Drive. Sign in again to get your logins back.") },
            confirmButton = {
                TextButton(onClick = { showSignOutDialog = false; viewModel.signOut() }) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (signedInEmail.isNotBlank()) {
                SectionCard("Account") {
                    SettingRow(label = signedInEmail, value = "Google Drive · encrypted with your master password")
                }
            }

            SectionCard("Security") {
                SettingRow(
                    label = "Change master password",
                    modifier = Modifier.clickable(onClick = onChangePassword),
                    trailing = { Chevron() }
                )
                Divider()
                SettingRow(
                    label = "New recovery key",
                    value = "Your current one stops working",
                    modifier = Modifier.clickable { showRecoveryDialog = true },
                    trailing = { Chevron() }
                )
                Divider()
                SettingRow(
                    label = "Fingerprint unlock",
                    value = if (biometricUnlock) "Fingerprint, face or screen lock" else "Master password every time",
                    modifier = Modifier.clickable { viewModel.setBiometricUnlock(!biometricUnlock) },
                    trailing = { Switch(checked = biometricUnlock, onCheckedChange = viewModel::setBiometricUnlock) }
                )
                Divider()
                SettingRow(
                    label = "Lock now",
                    modifier = Modifier.clickable(onClick = viewModel::lockNow)
                )
            }

            SectionCard("Sync") {
                SettingRow(
                    label = "Status",
                    value = when (val s = syncState) {
                        is SyncState.Syncing -> "Syncing…"
                        is SyncState.Success -> "Up to date"
                        is SyncState.Error   -> "Couldn't sync: ${s.message}"
                        else                 -> "Idle"
                    }
                )
                Divider()
                SettingRow(
                    label = "Last synced",
                    value = lastSyncTime?.let {
                        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(it))
                    } ?: "Not since the app opened"
                )
                Divider()
                val syncing = syncState is SyncState.Syncing
                SettingRow(
                    label = "Sync now",
                    modifier = Modifier.clickable(enabled = !syncing, onClick = viewModel::syncNow),
                    trailing = if (syncing) {
                        { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
                    } else null
                )
            }

            SectionCard("About") {
                SettingRow(label = "Version", trailing = {
                    Text(
                        appVersion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                })
            }

            SectionCard(title = null) {
                SettingRow(
                    label = "Sign out",
                    labelColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable { showSignOutDialog = true }
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Asks for the current master password, then replaces the recovery key. */
@Composable
private fun NewRecoveryKeyDialog(viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    val busy  by viewModel.recoveryBusy.collectAsState()
    val error by viewModel.recoveryError.collectAsState()
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("New recovery key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter your master password. Your current recovery key will stop working.")
                PasswordField(
                    password,
                    { password = it; viewModel.clearRecoveryError() },
                    label = "Master password",
                    isError = error != null
                )
                if (error != null) {
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.newRecoveryKey(password) },
                enabled = password.isNotEmpty() && !busy
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        }
    )
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
