package com.github.aashishvibhu.credentialmanagement.ui.vault

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aashishvibhu.credentialmanagement.data.vault.VaultAccess
import com.github.aashishvibhu.credentialmanagement.security.RecoveryKey
import com.github.aashishvibhu.credentialmanagement.security.WrongPasswordException
import com.github.aashishvibhu.credentialmanagement.security.WrongRecoveryKeyException
import com.github.aashishvibhu.credentialmanagement.ui.biometric.LockStateManager
import com.github.aashishvibhu.credentialmanagement.ui.components.PasswordField
import com.github.aashishvibhu.credentialmanagement.ui.components.SoftField
import com.github.aashishvibhu.credentialmanagement.util.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class UnlockMode {
    /** New phone or reinstall: unwrap the key from the vault in Drive. */
    DRIVE,
    /** Lock-screen fallback: the key is on this phone; just confirm the password (works offline). */
    LOCAL
}

/** Where to go after a successful unlock. */
enum class UnlockResult { OPENED, RECOVERED }

@HiltViewModel
class UnlockViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultAccess: VaultAccess,
    private val lockStateManager: LockStateManager
) : ViewModel() {

    val mode: UnlockMode = UnlockMode.valueOf(checkNotNull(savedStateHandle["mode"]))

    private val _input       = MutableStateFlow("")
    private val _useRecovery = MutableStateFlow(false)
    private val _busy        = MutableStateFlow(false)
    private val _error       = MutableStateFlow<String?>(null)
    private val _result      = MutableSharedFlow<UnlockResult>()

    val input:       StateFlow<String>  = _input.asStateFlow()
    val useRecovery: StateFlow<Boolean> = _useRecovery.asStateFlow()
    val busy:        StateFlow<Boolean> = _busy.asStateFlow()
    val error:       StateFlow<String?> = _error.asStateFlow()
    val result:      SharedFlow<UnlockResult> = _result

    fun onInputChange(v: String) { _input.value = v; _error.value = null }

    fun toggleRecovery() {
        _useRecovery.value = !_useRecovery.value
        _input.value = ""
        _error.value = null
    }

    fun submit() {
        val value = _input.value
        if (value.isBlank() || _busy.value) return
        if (_useRecovery.value && !RecoveryKey.isWellFormed(value)) {
            _error.value = "A recovery key has 24 letters and numbers, like ABCD-EFGH-…"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                val result = when {
                    _useRecovery.value -> { vaultAccess.unlockWithRecoveryKey(value); UnlockResult.RECOVERED }
                    mode == UnlockMode.DRIVE -> { vaultAccess.unlockWithPassword(value); UnlockResult.OPENED }
                    else -> { vaultAccess.verifyPassword(value); UnlockResult.OPENED }
                }
                lockStateManager.unlock()
                _result.emit(result)
            } catch (e: Exception) {
                _error.value = withAttemptsHint(e)
            } finally {
                _busy.value = false
            }
        }
    }

    private fun withAttemptsHint(e: Exception): String {
        val message = e.userMessage("Couldn't unlock")
        if (e !is WrongPasswordException && e !is WrongRecoveryKeyException) return message
        val left = vaultAccess.attemptsLeft()
        return when {
            left in 1..3 -> "$message $left ${if (left == 1) "try" else "tries"} left before a short wait."
            left == 0    -> "$message Wait a moment before trying again."
            else         -> message
        }
    }
}

@Composable
fun UnlockScreen(
    onUnlocked: (UnlockResult) -> Unit,
    viewModel: UnlockViewModel = hiltViewModel()
) {
    val input       by viewModel.input.collectAsState()
    val useRecovery by viewModel.useRecovery.collectAsState()
    val busy        by viewModel.busy.collectAsState()
    val error       by viewModel.error.collectAsState()

    LaunchedEffect(Unit) { viewModel.result.collect(onUnlocked) }

    val body = when {
        useRecovery -> "Type the recovery key you saved when you created your vault. You'll set a new master password next."
        viewModel.mode == UnlockMode.DRIVE ->
            "We found your vault in Google Drive. Enter your master password to open it on this phone."
        else -> "Enter your master password to open your vault."
    }

    VaultPage(
        icon = Icons.Outlined.Lock,
        title = if (useRecovery) "Use your recovery key" else "Unlock your vault",
        body = body,
        bottom = {
            PillButton("Unlock", onClick = viewModel::submit, loading = busy, enabled = input.isNotBlank())
            QuietButton(
                if (useRecovery) "Use master password instead" else "Use recovery key instead",
                onClick = viewModel::toggleRecovery,
                enabled = !busy
            )
        }
    ) {
        if (useRecovery) {
            SoftField(
                value = input,
                onValueChange = viewModel::onInputChange,
                label = "Recovery key",
                placeholder = "XXXX-XXXX-XXXX-XXXX-XXXX-XXXX",
                keyboardType = KeyboardType.Ascii,
                isError = error != null
            )
        } else {
            PasswordField(input, viewModel::onInputChange, label = "Master password", isError = error != null)
        }
        FieldError(error)
    }
}
