package com.github.aashishvibhu.credentialmanagement.ui.vault

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aashishvibhu.credentialmanagement.data.vault.VaultAccess
import com.github.aashishvibhu.credentialmanagement.data.vault.VaultStatus
import com.github.aashishvibhu.credentialmanagement.ui.components.PasswordField
import com.github.aashishvibhu.credentialmanagement.ui.components.StrengthMeter
import com.github.aashishvibhu.credentialmanagement.ui.theme.warningColor
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

/** Why a master password is being created. */
enum class CreateMode {
    /** Drive has no vault yet. */
    NEW,
    /** Move a readable old-format vault to a master password. */
    UPGRADE,
    /** Replace an unreadable old-format vault with an empty one. */
    REPLACE
}

const val MIN_MASTER_PASSWORD = 10

@HiltViewModel
class CreatePasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultAccess: VaultAccess
) : ViewModel() {

    val mode: CreateMode = CreateMode.valueOf(checkNotNull(savedStateHandle["mode"]))

    private val _password = MutableStateFlow("")
    private val _confirm  = MutableStateFlow("")
    private val _busy     = MutableStateFlow(false)
    private val _error    = MutableStateFlow<String?>(null)
    private val _done     = MutableSharedFlow<Unit>()

    val password: StateFlow<String>  = _password.asStateFlow()
    val confirm:  StateFlow<String>  = _confirm.asStateFlow()
    val busy:     StateFlow<Boolean> = _busy.asStateFlow()
    val error:    StateFlow<String?> = _error.asStateFlow()
    val done:     SharedFlow<Unit>   = _done

    fun onPasswordChange(v: String) { _password.value = v; _error.value = null }
    fun onConfirmChange(v: String)  { _confirm.value = v; _error.value = null }

    fun submit() {
        val pw = _password.value
        _error.value = when {
            pw.length < MIN_MASTER_PASSWORD -> "Use at least $MIN_MASTER_PASSWORD characters."
            pw != _confirm.value            -> "The passwords don't match."
            else                            -> null
        }
        if (_error.value != null || _busy.value) return

        viewModelScope.launch {
            _busy.value = true
            try {
                when (mode) {
                    CreateMode.NEW -> vaultAccess.create(pw, emptyList())
                    CreateMode.REPLACE -> vaultAccess.create(pw, emptyList(), replaceExisting = true)
                    CreateMode.UPGRADE -> {
                        val status = vaultAccess.checkRemote() as? VaultStatus.LegacyReadable
                            ?: throw IllegalStateException("Your old vault changed. Go back and try again.")
                        vaultAccess.create(pw, status.credentials)
                    }
                }
                _done.emit(Unit)
            } catch (e: Exception) {
                _error.value = e.userMessage("Couldn't create your vault")
            } finally {
                _busy.value = false
            }
        }
    }
}

@Composable
fun CreatePasswordScreen(
    onCreated: () -> Unit,
    viewModel: CreatePasswordViewModel = hiltViewModel()
) {
    val password by viewModel.password.collectAsState()
    val confirm  by viewModel.confirm.collectAsState()
    val busy     by viewModel.busy.collectAsState()
    val error    by viewModel.error.collectAsState()

    LaunchedEffect(Unit) { viewModel.done.collect { onCreated() } }

    val (title, body) = when (viewModel.mode) {
        CreateMode.UPGRADE -> "Set a master password" to
            "Your vault is moving to a master password so you can open it on any phone. Your logins stay as they are."
        else -> "Create a master password" to
            "It locks your vault in Google Drive. You'll need it to open your vault on a new phone."
    }

    VaultPage(
        icon = Icons.Outlined.Key,
        title = title,
        body = body,
        bottom = {
            PillButton("Continue", onClick = viewModel::submit, loading = busy)
        }
    ) {
        PasswordField(password, viewModel::onPasswordChange, label = "Master password")
        if (password.isNotEmpty()) StrengthMeter(passwordStrength(password))
        PasswordField(
            confirm,
            viewModel::onConfirmChange,
            label = "Confirm password",
            isError = confirm.isNotEmpty() && confirm != password
        )
        FieldError(error)
        Surface(
            shape = MaterialTheme.shapes.small,
            color = warningColor.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.WarningAmber, null, tint = warningColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Nobody can reset this password, not even us. You'll get a recovery key next as your backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
