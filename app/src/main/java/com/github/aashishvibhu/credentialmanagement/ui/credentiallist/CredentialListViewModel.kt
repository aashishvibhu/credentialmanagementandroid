package com.github.aashishvibhu.credentialmanagement.ui.credentiallist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aashishvibhu.credentialmanagement.data.local.LocalCredentialRepository
import com.github.aashishvibhu.credentialmanagement.domain.model.Credential
import com.github.aashishvibhu.credentialmanagement.sync.SyncManager
import com.github.aashishvibhu.credentialmanagement.sync.SyncScheduler
import com.github.aashishvibhu.credentialmanagement.sync.SyncState
import com.github.aashishvibhu.credentialmanagement.ui.biometric.LockStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val syncManager: SyncManager,
    private val syncScheduler: SyncScheduler,
    private val lockStateManager: LockStateManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val syncState: StateFlow<SyncState> = syncManager.syncState

    // Gated on the lock state: while locked, the decrypted list is wiped from memory
    // (emits emptyList). On unlock the upstream Room flow re-emits and refills it.
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

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }

    fun deleteCredential(credential: Credential) {
        recentlyDeleted = credential
        viewModelScope.launch {
            localRepo.delete(credential.id)
            syncScheduler.scheduleImmediateSync()
        }
    }

    fun undoDelete() {
        recentlyDeleted?.let { cred ->
            viewModelScope.launch {
                localRepo.save(cred, isDirty = true)
                syncScheduler.scheduleImmediateSync()
            }
        }
        recentlyDeleted = null
    }

    fun copyToClipboard(context: Context, text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        viewModelScope.launch {
            delay(30_000)
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            }
        }
    }
}
