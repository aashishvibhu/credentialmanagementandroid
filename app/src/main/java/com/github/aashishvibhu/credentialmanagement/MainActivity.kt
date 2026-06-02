package com.github.aashishvibhu.credentialmanagement

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import com.github.aashishvibhu.credentialmanagement.ui.theme.CredentialManagementTheme
import dagger.hilt.android.AndroidEntryPoint
 
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()
        setContent {
            CredentialManagementTheme {
                Surface {
                    // NavGraph will be wired in Phase 7
                }
            }
        }
    }
}
