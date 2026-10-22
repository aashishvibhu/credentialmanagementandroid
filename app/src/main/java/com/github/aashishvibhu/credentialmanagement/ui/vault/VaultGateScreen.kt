package com.github.aashishvibhu.credentialmanagement.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aashishvibhu.credentialmanagement.data.vault.VaultAccess
import com.github.aashishvibhu.credentialmanagement.data.vault.VaultStatus
import com.github.aashishvibhu.credentialmanagement.util.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface GateState {
    data object Checking : GateState
    data class Failed(val message: String) : GateState
    data class Resolved(val status: VaultStatus) : GateState
}

@HiltViewModel
class VaultGateViewModel @Inject constructor(
    private val vaultAccess: VaultAccess
) : ViewModel() {

    private val _state = MutableStateFlow<GateState>(GateState.Checking)
    val state: StateFlow<GateState> = _state.asStateFlow()

    init { check() }

    fun check() {
        // A phone that already holds the key opens without the network
        if (vaultAccess.isUnlockedOnThisPhone()) {
            _state.value = GateState.Resolved(VaultStatus.Ready)
            return
        }
        _state.value = GateState.Checking
        viewModelScope.launch {
            _state.value = try {
                GateState.Resolved(vaultAccess.checkRemote())
            } catch (e: Exception) {
                GateState.Failed(e.userMessage("Couldn't check your vault"))
            }
        }
    }
}

/** Decides where to go after sign-in: create, unlock, upgrade or recover the vault. */
@Composable
fun VaultGateScreen(
    onResolved: (VaultStatus) -> Unit,
    onSignOut: () -> Unit,
    viewModel: VaultGateViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        (state as? GateState.Resolved)?.let { onResolved(it.status) }
    }

    when (val s = state) {
        is GateState.Failed -> VaultPage(
            icon = Icons.Outlined.CloudOff,
            title = "Couldn't reach your vault",
            body = s.message,
            bottom = {
                PillButton("Try again", onClick = viewModel::check)
                QuietButton("Sign out", onClick = onSignOut)
            }
        )
        else -> Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
            ) {
                CircularProgressIndicator()
                Text(
                    "Checking your vault…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
