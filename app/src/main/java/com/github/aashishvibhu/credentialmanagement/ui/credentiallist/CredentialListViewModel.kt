package com.github.aashishvibhu.credentialmanagement.ui.credentiallist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aashishvibhu.credentialmanagement.data.local.LocalCredentialRepository
import com.github.aashishvibhu.credentialmanagement.domain.model.Credential
import com.github.aashishvibhu.credentialmanagement.sync.VaultSyncManager
import com.github.aashishvibhu.credentialmanagement.sync.SyncState
import com.github.aashishvibhu.credentialmanagement.ui.biometric.LockStateManager
import com.github.aashishvibhu.credentialmanagement.util.ConnectivityChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CredentialListViewModel @Inject constructor(
    private val localRepo: LocalCredentialRepository,
    private val vaultSyncManager: VaultSyncManager,
    private val lockStateManager: LockStateManager,
    private val connectivityChecker: ConnectivityChecker
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val syncState: StateFlow<SyncState> = vaultSyncManager.syncState

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Gated on the lock state: while locked, the decrypted list is wiped from memory
    val credentials: StateFlow<List<Credential>> = combine(
        localRepo.getAll(),
        _searchQuery,
        lockStateManager.isLocked
    ) { list, query, locked ->
        when {
            locked -> emptyList()
            query.isBlank() -> list
            else -> list.filter { c ->
                c.title.contains(query, ignoreCase = true) ||
                c.username.contains(query, ignoreCase = true) ||
                c.url.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var recentlyDeleted: Credential? = null
    private var clipboardClearJob: Job? = null

    fun clearError() { _errorMessage.value = null }

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }

    fun deleteCredential(credential: Credential) {
        recentlyDeleted = credential
        viewModelScope.launch {
            if (!connectivityChecker.isOnline()) {
                _errorMessage.value = "No internet connection."
                recentlyDeleted = null
                return@launch
            }
            try {
                val remote = vaultSyncManager.pullLatestCredentials()
                val reduced = remote.filter { it.id != credential.id }
                vaultSyncManager.pushFullVault(reduced)
                localRepo.delete(credential.id)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to delete."
                recentlyDeleted = null
            }
        }
    }

    fun undoDelete() {
        recentlyDeleted?.let { cred ->
            viewModelScope.launch {
                if (!connectivityChecker.isOnline()) {
                    _errorMessage.value = "No internet connection."
                    return@launch
                }
                try {
                    val remote = vaultSyncManager.pullLatestCredentials()
                    val restored = remote + cred
                    vaultSyncManager.pushFullVault(restored)
                    localRepo.save(cred)
                } catch (e: Exception) {
                    _errorMessage.value = e.message ?: "Failed to restore."
                }
            }
        }
        recentlyDeleted = null
    }

    fun copyToClipboard(context: Context, text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        // Cancel any pending clear and schedule a new one that only clears if this text is still on clipboard
        clipboardClearJob?.cancel()
        clipboardClearJob = viewModelScope.launch {
            delay(30_000)
            val currentClip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            if (currentClip == text) {
                clipboard.clearPrimaryClip()
            }
        }
    }
}
