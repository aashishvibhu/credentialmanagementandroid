package com.github.aashishvibhu.credentialmanagement.ui.biometric

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LockStateManager @Inject constructor() {

    companion object {
        private const val LOCK_TIMEOUT_MS = 60_000L
    }

    private val _isLocked = MutableStateFlow(true)  // locked on every cold start
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var lastBackgroundedAt: Long = 0L

    /** Called from MainActivity.onResume — locks if the app was backgrounded for >60 s. */
    fun onForegrounded() {
        val now = System.currentTimeMillis()
        if (lastBackgroundedAt > 0L && (now - lastBackgroundedAt) > LOCK_TIMEOUT_MS) {
            _isLocked.value = true
        }
        lastBackgroundedAt = 0L
    }

    /** Called from MainActivity.onPause — records the time the app left the foreground. */
    fun onBackgrounded() {
        lastBackgroundedAt = System.currentTimeMillis()
    }

    fun unlock() { _isLocked.value = false }

    fun lock()   { _isLocked.value = true }
}
