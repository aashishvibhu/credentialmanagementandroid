package com.github.aashishvibhu.credentialmanagement.ui.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val syncState    by viewModel.syncState.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val signedInEmail by viewModel.signedInEmail.collectAsState()
    val context = LocalContext.current
    var showSignOutDialog by remember { mutableStateOf(false) }

    val appVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
        } catch (_: PackageManager.NameNotFoundException) { "—" }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out") },
            text = { Text("Your vault remains encrypted in Google Drive. Sign in again to restore your credentials.") },
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
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Account
            if (signedInEmail.isNotBlank()) {
                ListItem(
                    headlineContent = { Text("Signed in as") },
                    supportingContent = { Text(signedInEmail) }
                )
                HorizontalDivider()
            }

            // Sync status
            ListItem(
                headlineContent = { Text("Sync status") },
                supportingContent = {
                    Text(
                        when (syncState) {
                            is SyncState.Syncing -> "Syncing…"
                            is SyncState.Success -> "Up to date"
                            is SyncState.Error   -> "Error: ${(syncState as SyncState.Error).message}"
                            else                 -> "Idle"
                        }
                    )
                }
            )

            // Last sync time
            ListItem(
                headlineContent = { Text("Last synced") },
                supportingContent = {
                    Text(
                        lastSyncTime?.let {
                            SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(it))
                        } ?: "Never"
                    )
                }
            )

            HorizontalDivider()

            // Sync now
            OutlinedButton(
                onClick = viewModel::syncNow,
                enabled = syncState !is SyncState.Syncing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Sync Now")
            }

            HorizontalDivider()

            // App version
            ListItem(
                headlineContent = { Text("App version") },
                supportingContent = { Text(appVersion) }
            )

            HorizontalDivider()

            Spacer(Modifier.height(16.dp))

            // Sign out
            Button(
                onClick = { showSignOutDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor   = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("Sign Out")
            }
        }
    }
}
