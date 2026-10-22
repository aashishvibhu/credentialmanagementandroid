package com.github.aashishvibhu.credentialmanagement.ui.credentiallist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.aashishvibhu.credentialmanagement.domain.model.Credential
import com.github.aashishvibhu.credentialmanagement.sync.SyncState
import com.github.aashishvibhu.credentialmanagement.ui.components.ServiceAvatar
import kotlinx.coroutines.launch

@Composable
fun CredentialListScreen(
    onAddNew: (service: String?) -> Unit,
    onEdit: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: CredentialListViewModel = hiltViewModel()
) {
    val entries      by viewModel.entries.collectAsState()
    val credentials  by viewModel.credentials.collectAsState()
    val searchQuery  by viewModel.searchQuery.collectAsState()
    val syncState    by viewModel.syncState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val context      = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Keys of the group rows the user has opened; survives rotation
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    val searching = searchQuery.isNotBlank()

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel.clearError()
        }
    }

    fun copy(text: String, label: String) {
        viewModel.copyToClipboard(context, text, label)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar("${label.replaceFirstChar { it.uppercase() }} copied")
        }
    }

    fun delete(credential: Credential) {
        viewModel.deleteCredential(credential)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "\"${credential.title}\" deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAddNew(null) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Header(
                count = credentials.size,
                syncState = syncState,
                searchQuery = searchQuery,
                onSearchChange = viewModel::onSearchQueryChange,
                onSettings = onSettings
            )

            when {
                entries.isEmpty() && !searching -> EmptyState(
                    title = "Your vault is empty",
                    body = "Add your first login to get started. It's encrypted before it leaves your phone."
                )
                entries.isEmpty() -> EmptyState(
                    title = "No matches",
                    body = "Nothing in your vault matches \"$searchQuery\"."
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(entries, key = { it.key }) { entry ->
                        when (entry) {
                            is VaultEntry.Single -> SwipeToDelete(onDelete = { delete(entry.credential) }) {
                                LoginRow(
                                    credential = entry.credential,
                                    onOpen = { onEdit(entry.credential.id) },
                                    onCopy = ::copy
                                )
                            }
                            is VaultEntry.Group -> GroupRow(
                                group = entry,
                                isExpanded = searching || entry.key in expanded,
                                onToggle = {
                                    expanded = if (entry.key in expanded) expanded - entry.key
                                               else expanded + entry.key
                                },
                                onOpen = onEdit,
                                onCopy = ::copy,
                                onDelete = ::delete,
                                onAddAccount = { onAddNew(entry.name) }
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 76.dp, end = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

// ── Header ─────────────────────────────────────────────────────────────────────

@Composable
private fun Header(
    count: Int,
    syncState: SyncState,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSettings: () -> Unit
) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SyncChip(syncState)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text("Vault", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = if (count == 1) "1 login" else "$count logins",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        SearchField(
            query = searchQuery,
            onChange = onSearchChange,
            modifier = Modifier.padding(end = 8.dp)
        )
    }
}

@Composable
private fun SyncChip(state: SyncState) {
    val colors = MaterialTheme.colorScheme
    val (label, color) = when (state) {
        is SyncState.Syncing -> "Syncing" to colors.onSurfaceVariant
        is SyncState.Success -> "Synced" to colors.tertiary
        is SyncState.Error   -> "Sync failed" to colors.error
        else                 -> "Encrypted" to colors.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when (state) {
            is SyncState.Syncing -> CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                strokeWidth = 1.5.dp,
                color = color
            )
            is SyncState.Success, is SyncState.Error -> Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            else -> Icon(Icons.Outlined.Lock, null, tint = color, modifier = Modifier.size(14.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = colors.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = 4.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Search, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Search logins", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Outlined.Close, "Clear search", tint = colors.onSurfaceVariant)
                }
            }
        }
    }
}

// ── Rows ───────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoginRow(
    credential: Credential,
    onOpen: () -> Unit,
    onCopy: (text: String, label: String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .combinedClickable(onClick = onOpen, onLongClick = { menuExpanded = true })
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ServiceAvatar(credential.title)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    credential.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    credential.username,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            CopyPasswordButton { onCopy(credential.password, "password") }
        }
        CopyMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            credential = credential,
            onCopy = onCopy
        )
    }
}

@Composable
private fun GroupRow(
    group: VaultEntry.Group,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onOpen: (String) -> Unit,
    onCopy: (text: String, label: String) -> Unit,
    onDelete: (Credential) -> Unit,
    onAddAccount: () -> Unit
) {
    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "chevron")
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ServiceAvatar(group.name)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${group.accounts.size} accounts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation)
            )
        }
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                group.accounts.forEach { account ->
                    // key() keeps swipe state attached to the right account as the list changes
                    key(account.id) {
                        SwipeToDelete(onDelete = { onDelete(account) }) {
                            AccountRow(account, onOpen = { onOpen(account.id) }, onCopy = onCopy)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAddAccount)
                        .padding(start = 76.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Add another ${group.name} account",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountRow(
    account: Credential,
    onOpen: () -> Unit,
    onCopy: (text: String, label: String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .combinedClickable(onClick = onOpen, onLongClick = { menuExpanded = true })
                .padding(start = 76.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    account.username,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
            CopyPasswordButton { onCopy(account.password, "password") }
        }
        CopyMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            credential = account,
            onCopy = onCopy
        )
    }
}

@Composable
private fun CopyPasswordButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Outlined.ContentCopy,
            contentDescription = "Copy password",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun CopyMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    credential: Credential,
    onCopy: (text: String, label: String) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Copy username") },
            onClick = { onDismiss(); onCopy(credential.username, "username") }
        )
        DropdownMenuItem(
            text = { Text("Copy password") },
            onClick = { onDismiss(); onCopy(credential.password, "password") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) { content() }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
