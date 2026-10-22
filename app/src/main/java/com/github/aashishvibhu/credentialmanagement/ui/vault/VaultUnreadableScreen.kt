package com.github.aashishvibhu.credentialmanagement.ui.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Old-format vault whose key was lost with an earlier install. */
@Composable
fun VaultUnreadableScreen(
    onStartNewVault: () -> Unit,
    onSignOut: () -> Unit
) {
    var confirming by remember { mutableStateOf(false) }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Replace the old vault?") },
            text = { Text("The vault in Google Drive will be replaced with a new, empty one. Logins in the old vault can't be recovered.") },
            confirmButton = {
                TextButton(onClick = { confirming = false; onStartNewVault() }) {
                    Text("Replace", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } }
        )
    }

    VaultPage(
        icon = Icons.Outlined.LockReset,
        danger = true,
        title = "This vault can't be opened",
        body = "It was locked with a key that only existed in an earlier install of this app. " +
            "That install is gone, so nobody can open this vault, including us.",
        bottom = {
            PillButton("Start a new vault", onClick = { confirming = true }, danger = true)
            QuietButton("Sign out", onClick = onSignOut)
        }
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Have the old app on another phone?", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Open it there first. After updating, it will offer to move your logins to a master password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
