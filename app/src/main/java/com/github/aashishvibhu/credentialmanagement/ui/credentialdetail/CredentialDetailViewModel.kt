package com.github.aashishvibhu.credentialmanagement.ui.credentialdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aashishvibhu.credentialmanagement.data.local.LocalCredentialRepository
import com.github.aashishvibhu.credentialmanagement.domain.model.Credential
import com.github.aashishvibhu.credentialmanagement.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CredentialDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val localRepo: LocalCredentialRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    val credentialId: String = checkNotNull(savedStateHandle["credentialId"])
    val isNew: Boolean = credentialId == "new"

    private val _title    = MutableStateFlow("")
    private val _username = MutableStateFlow("")
    private val _password = MutableStateFlow("")
    private val _url      = MutableStateFlow("")
    private val _notes    = MutableStateFlow("")

    private val _passwordVisible = MutableStateFlow(false)
    private val _isLoading       = MutableStateFlow(!isNew)
    private val _isSaving        = MutableStateFlow(false)
    private val _navEvent        = MutableSharedFlow<Unit>()

    val title:           StateFlow<String>  = _title.asStateFlow()
    val username:        StateFlow<String>  = _username.asStateFlow()
    val password:        StateFlow<String>  = _password.asStateFlow()
    val url:             StateFlow<String>  = _url.asStateFlow()
    val notes:           StateFlow<String>  = _notes.asStateFlow()
    val passwordVisible: StateFlow<Boolean> = _passwordVisible.asStateFlow()
    val isLoading:       StateFlow<Boolean> = _isLoading.asStateFlow()
    val isSaving:        StateFlow<Boolean> = _isSaving.asStateFlow()
    val navEvent:        SharedFlow<Unit>   = _navEvent

    val canSave: StateFlow<Boolean> = combine(_title, _username) { t, u ->
        t.isNotBlank() && u.isNotBlank()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val passwordStrength: StateFlow<Float> = _password.map { computeStrength(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    private var originalCreatedAt: Long = 0L

    init { if (!isNew) load() }

    private fun load() = viewModelScope.launch {
        localRepo.getById(credentialId)?.let { c ->
            _title.value    = c.title
            _username.value = c.username
            _password.value = c.password
            _url.value      = c.url
            _notes.value    = c.notes
            originalCreatedAt = c.createdAt
        }
        _isLoading.value = false
    }

    fun onTitleChange(v: String)    { _title.value = v }
    fun onUsernameChange(v: String) { _username.value = v }
    fun onPasswordChange(v: String) { _password.value = v }
    fun onUrlChange(v: String)      { _url.value = v }
    fun onNotesChange(v: String)    { _notes.value = v }
    fun togglePasswordVisibility()  { _passwordVisible.value = !_passwordVisible.value }

    fun generatePassword() {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#\$%^&*"
        _password.value = (1..16).map { chars.random() }.joinToString("")
    }

    fun save() {
        if (!canSave.value) return
        viewModelScope.launch {
            _isSaving.value = true
            val now = System.currentTimeMillis()
            val credential = if (isNew) {
                Credential(
                    title    = _title.value.trim(),
                    username = _username.value.trim(),
                    password = _password.value,
                    url      = _url.value.trim(),
                    notes    = _notes.value.trim(),
                    createdAt = now,
                    updatedAt = now
                )
            } else {
                Credential(
                    id       = credentialId,
                    title    = _title.value.trim(),
                    username = _username.value.trim(),
                    password = _password.value,
                    url      = _url.value.trim(),
                    notes    = _notes.value.trim(),
                    createdAt = originalCreatedAt,
                    updatedAt = now
                )
            }
            localRepo.save(credential)
            try { syncManager.pushNow() } catch (_: Exception) { /* credential is dirty; periodic sync will retry */ }
            _navEvent.emit(Unit)
            _isSaving.value = false
        }
    }

    fun delete() {
        if (isNew) return
        viewModelScope.launch {
            _isSaving.value = true
            localRepo.delete(credentialId)
            try { syncManager.pushNow() } catch (_: Exception) { /* periodic sync will retry */ }
            _navEvent.emit(Unit)
            _isSaving.value = false
        }
    }

    private fun computeStrength(p: String): Float {
        if (p.isEmpty()) return 0f
        var s = 0
        if (p.length >= 8) s++
        if (p.length >= 12) s++
        if (p.any { it.isUpperCase() }) s++
        if (p.any { it.isLowerCase() }) s++
        if (p.any { it.isDigit() }) s++
        if (p.any { "!@#\$%^&*()_+-=[]{}|;':\",./<>?".contains(it) }) s++
        return s / 6f
    }
}
