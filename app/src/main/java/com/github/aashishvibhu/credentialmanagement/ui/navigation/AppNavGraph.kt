package com.github.aashishvibhu.credentialmanagement.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.aashishvibhu.credentialmanagement.ui.auth.SignInScreen

object Routes {
    const val SIGN_IN = "sign_in"
    const val CREDENTIAL_LIST = "credential_list"
}

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SIGN_IN
    ) {
        composable(Routes.SIGN_IN) {
            SignInScreen(
                onSignedIn = {
                    navController.navigate(Routes.CREDENTIAL_LIST) {
                        popUpTo(Routes.SIGN_IN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.CREDENTIAL_LIST) {
            // Placeholder — full implementation in Phase 7
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Credential List — coming in Phase 7")
            }
        }
    }
}
