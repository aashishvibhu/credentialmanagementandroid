package com.github.aashishvibhu.credentialmanagement

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.aashishvibhu.credentialmanagement.data.auth.AuthRepository
import com.github.aashishvibhu.credentialmanagement.sync.SyncScheduler
import com.github.aashishvibhu.credentialmanagement.ui.navigation.AppNavGraph
import com.github.aashishvibhu.credentialmanagement.ui.theme.CredentialManagementTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var syncScheduler: SyncScheduler

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
        if (authRepository.getSignedInAccount() != null) {
            syncScheduler.scheduleImmediateSync()
        }
    }
}
