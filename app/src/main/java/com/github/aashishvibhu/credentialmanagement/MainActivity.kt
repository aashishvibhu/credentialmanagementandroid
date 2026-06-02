package com.github.aashishvibhu.credentialmanagement

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.github.aashishvibhu.credentialmanagement.data.auth.AuthRepository
import com.github.aashishvibhu.credentialmanagement.sync.SyncScheduler
import com.github.aashishvibhu.credentialmanagement.ui.biometric.LockStateManager
import com.github.aashishvibhu.credentialmanagement.ui.navigation.AppNavGraph
import com.github.aashishvibhu.credentialmanagement.ui.theme.CredentialManagementTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var lockStateManager: LockStateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()
        setContent {
            CredentialManagementTheme {
                AppNavGraph()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-lock the vault if the app was backgrounded past the timeout window.
        lockStateManager.onForegrounded()
        if (authRepository.getSignedInAccount() != null) {
            syncScheduler.scheduleImmediateSync()
        }
    }

    override fun onPause() {
        super.onPause()
        // Record when the app left the foreground so onForegrounded can apply the timeout.
        lockStateManager.onBackgrounded()
    }
}
