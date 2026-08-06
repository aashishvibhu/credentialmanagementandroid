package com.github.aashishvibhu.credentialmanagement.sync

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    object Success : SyncState()
    object Skipped : SyncState()
    data class Error(val message: String) : SyncState()
}
