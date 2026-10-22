package com.github.aashishvibhu.credentialmanagement.ui.vault

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aashishvibhu.credentialmanagement.data.vault.VaultAccess
import com.github.aashishvibhu.credentialmanagement.ui.components.PasswordField
import com.github.aashishvibhu.credentialmanagement.ui.components.StrengthMeter
import com.github.aashishvibhu.credentialmanagement.util.passwordStrength
import com.github.aashishvibhu.credentialmanagement.util.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ChangeMode {
    /** From Settings: needs the current password. */
    CHANGE,
    /** Right after unlocking with the recovery key: the old password is unknown. */
    RESET
}

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultAccess: VaultAccess
) : ViewModel() {

    val mode: ChangeMode = ChangeMode.valueOf(checkNotNull(savedStateHandle["mode"]))

    private val _current = MutableStateFlow("")
    private val _new     = MutableStateFlow("")
    private val _confirm = MutableStateFlow("")
    private val _busy    = MutableStateFlow(false)
    private val _error   = MutableStateFlow<String?>(null)
    private val _done    = MutableSharedFlow<Unit>()

    val current: StateFlow<String>  = _current.asStateFlow()
    val new:     StateFlow<String>  = _new.asStateFlow()
    val confirm: StateFlow<String>  = _confirm.asStateFlow()
    val busy:    StateFlow<Boolean> = _busy.asStateFlow()
    val error:   StateFlow<String?> = _error.asStateFlow()
    val done:    SharedFlow<Unit>   = _done

    fun onCurrentChange(v: String) { _current.value = v; _error.value = null }
    fun onNewChange(v: String)     { _new.value = v; _error.value = null }
    fun onConfirmChange(v: String) { _confirm.value = v; _error.value = null }

    fun submit() {
        val pw = _new.value
        _error.value = when {
            mode == ChangeMode.CHANGE && _current.value.isEmpty() -> "Enter your current master password."
            pw.length < MIN_MASTER_PASSWORD -> "Use at least $MIN_MASTER_PASSWORD characters."
            pw != _confirm.value -> "The new passwords don't match."
            else -> null
        }
        if (_error.value != null || _busy.value) return

        viewModelScope.launch {
            _busy.value = true
            try {
                when (mode) {
                    ChangeMode.CHANGE -> vaultAccess.changePassword(_current.value, pw)
                    ChangeMode.RESET  -> vaultAccess.resetPassword(pw)
                }
                _done.emit(Unit)
            } catch (e: Exception) {
                _error.value = e.userMessage("Couldn't change your password")
            } finally {
                _busy.value = false
            }
        }
    }
}

@Composable
fun ChangePasswordScreen(
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel()
) {
    val current by viewModel.current.collectAsState()
    val new     by viewModel.new.collectAsState()
    val confirm by viewModel.confirm.collectAsState()
    val busy    by viewModel.busy.collectAsState()
    val error   by viewModel.error.collectAsState()

    LaunchedEffect(Unit) { viewModel.done.collect { onDone() } }

    val reset = viewModel.mode == ChangeMode.RESET
    VaultPage(
        icon = Icons.Outlined.Key,
        title = if (reset) "Set a new master password" else "Change master password",
        body = if (reset) "Your recovery key opened the vault. Choose a new master password for next time."
               else "Your other phones will ask for the new password the next time they unlock.",
        bottom = {
            PillButton(if (reset) "Save password" else "Change password", onClick = viewModel::submit, loading = busy)
            if (!reset) QuietButton("Cancel", onClick = onCancel, enabled = !busy)
        }
    ) {
        if (!reset) PasswordField(current, viewModel::onCurrentChange, label = "Current password")
        PasswordField(new, viewModel::onNewChange, label = "New password")
        if (new.isNotEmpty()) StrengthMeter(passwordStrength(new))
        PasswordField(
            confirm,
            viewModel::onConfirmChange,
            label = "Confirm new password",
            isError = confirm.isNotEmpty() && confirm != new
        )
        FieldError(error)
    }
}
