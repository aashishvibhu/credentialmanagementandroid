package com.github.aashishvibhu.credentialmanagement.ui.credentialdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.aashishvibhu.credentialmanagement.ui.components.ServiceAvatar
import com.github.aashishvibhu.credentialmanagement.ui.components.SoftField
import com.github.aashishvibhu.credentialmanagement.ui.components.StrengthMeter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialDetailScreen(
    onBack: () -> Unit,
    viewModel: CredentialDetailViewModel = hiltViewModel()
) {
    val title           by viewModel.title.collectAsState()
    val username        by viewModel.username.collectAsState()
    val password        by viewModel.password.collectAsState()
    val url             by viewModel.url.collectAsState()
    val notes           by viewModel.notes.collectAsState()
    val passwordVisible by viewModel.passwordVisible.collectAsState()
    val isLoading       by viewModel.isLoading.collectAsState()
    val isSaving        by viewModel.isSaving.collectAsState()
    val canSave         by viewModel.canSave.collectAsState()
    val strength        by viewModel.passwordStrength.collectAsState()
    val errorMessage    by viewModel.errorMessage.collectAsState()
    val suggestions     by viewModel.serviceSuggestions.collectAsState()
    val sameService     by viewModel.sameServiceCount.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.navEvent) {
        viewModel.navEvent.collect { onBack() }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel.clearError()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete login?") },
            text = { Text("\"$title\" will be removed from your vault on every device.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.delete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "New login" else "Edit login") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.Close, "Close")
                    }
                },
                actions = {
                    Button(
                        onClick = viewModel::save,
                        enabled = canSave && !isSaving,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Save")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live preview of how the login will appear in the list
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    ServiceAvatar(title.ifBlank { "?" }, size = 52.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            title.ifBlank { "Service name" },
                            style = MaterialTheme.typography.titleLarge,
                            color = if (title.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        if (sameService > 0) {
                            Text(
                                if (sameService == 1) "Grouped with 1 other account"
                                else "Grouped with $sameService other accounts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SoftField(
                    value = title,
                    onValueChange = viewModel::onTitleChange,
                    label = "Service",
                    placeholder = "Google"
                )

                if (suggestions.isNotEmpty()) {
                    ServiceSuggestions(suggestions, onPick = viewModel::onTitleChange)
                }

                SoftField(
                    value = username,
                    onValueChange = viewModel::onUsernameChange,
                    label = "Username or email",
                    placeholder = "name@gmail.com",
                    keyboardType = KeyboardType.Email
                )

                SoftField(
                    value = password,
                    onValueChange = viewModel::onPasswordChange,
                    label = "Password",
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = viewModel::generatePassword) {
                                Icon(Icons.Outlined.AutoAwesome, "Generate password")
                            }
                            IconButton(onClick = viewModel::togglePasswordVisibility) {
                                Icon(
                                    if (passwordVisible) Icons.Outlined.VisibilityOff
                                    else Icons.Outlined.Visibility,
                                    if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        }
                    }
                )

                if (password.isNotEmpty()) {
                    StrengthMeter(strength)
                }

                SoftField(
                    value = url,
                    onValueChange = viewModel::onUrlChange,
                    label = "Website",
                    placeholder = "accounts.google.com",
                    keyboardType = KeyboardType.Uri
                )

                SoftField(
                    value = notes,
                    onValueChange = viewModel::onNotesChange,
                    label = "Notes",
                    singleLine = false
                )

                if (!viewModel.isNew) {
                    TextButton(
                        onClick = { showDeleteDialog = true },
                        enabled = !isSaving,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Delete login", color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Chips for existing service names; picking one puts this login in that service's group. */
@Composable
private fun ServiceSuggestions(names: List<String>, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Add to an existing service",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            names.forEach { name ->
                SuggestionChip(
                    onClick = { onPick(name) },
                    label = { Text(name) },
                    icon = { ServiceAvatar(name, size = 18.dp) },
                    shape = RoundedCornerShape(50),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    border = null
                )
            }
        }
    }
}
