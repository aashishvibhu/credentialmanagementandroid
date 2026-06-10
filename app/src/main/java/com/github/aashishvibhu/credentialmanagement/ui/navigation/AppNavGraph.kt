package com.github.aashishvibhu.credentialmanagement.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.aashishvibhu.credentialmanagement.data.auth.AuthState
import com.github.aashishvibhu.credentialmanagement.ui.auth.AuthViewModel
import com.github.aashishvibhu.credentialmanagement.ui.auth.SignInScreen
import com.github.aashishvibhu.credentialmanagement.ui.biometric.BiometricLockScreen
import com.github.aashishvibhu.credentialmanagement.ui.biometric.LockViewModel
import com.github.aashishvibhu.credentialmanagement.ui.credentialdetail.CredentialDetailScreen
import com.github.aashishvibhu.credentialmanagement.ui.credentiallist.CredentialListScreen
import com.github.aashishvibhu.credentialmanagement.ui.settings.SettingsScreen

object Routes {
    const val SIGN_IN         = "sign_in"
    const val BIOMETRIC_LOCK  = "biometric_lock"
    const val CREDENTIAL_LIST = "credential_list"
    const val CREDENTIAL_DETAIL = "credential_detail"
    const val SETTINGS        = "settings"

    fun credentialDetail(id: String = "{credentialId}") = "$CREDENTIAL_DETAIL/$id"
}

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController()
) {
    // Share a single AuthViewModel across the whole graph so sign-out is observed globally
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsState()

    // Navigate to sign-in whenever auth transitions from a non-Idle state back to Idle (sign-out)
    var prevAuthState by remember { mutableStateOf<AuthState?>(null) }
    LaunchedEffect(authState) {
        val prev = prevAuthState
        if (prev != null && prev !is AuthState.Idle && authState is AuthState.Idle) {
            navController.navigate(Routes.SIGN_IN) {
                popUpTo(0) { inclusive = true }
            }
        }
        prevAuthState = authState
    }

    // Re-lock: when the vault locks (background timeout) while signed in, redirect to the
    // biometric screen. The full back stack is cleared so no protected screen lingers behind it.
    val lockViewModel: LockViewModel = hiltViewModel()
    val isLocked by lockViewModel.isLocked.collectAsState()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(isLocked, currentRoute, authState) {
        if (isLocked &&
            authState is AuthState.SignedIn &&
            currentRoute != null &&
            currentRoute != Routes.SIGN_IN &&
            currentRoute != Routes.BIOMETRIC_LOCK
        ) {
            navController.navigate(Routes.BIOMETRIC_LOCK) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SIGN_IN
    ) {

        // ── Sign-in ───────────────────────────────────────────────────────────
        composable(Routes.SIGN_IN) {
            SignInScreen(
                viewModel = authViewModel,
                onSignedIn = {
                    navController.navigate(Routes.BIOMETRIC_LOCK) {
                        popUpTo(Routes.SIGN_IN) { inclusive = true }
                    }
                }
            )
        }

        // ── Biometric lock ────────────────────────────────────────────────────
        composable(Routes.BIOMETRIC_LOCK) {
            BiometricLockScreen(
                onAuthenticated = {
                    navController.navigate(Routes.CREDENTIAL_LIST) {
                        popUpTo(Routes.BIOMETRIC_LOCK) { inclusive = true }
                    }
                }
            )
        }

        // ── Credential list ───────────────────────────────────────────────────
        composable(Routes.CREDENTIAL_LIST) {
            CredentialListScreen(
                onAddNew = { navController.navigate(Routes.credentialDetail("new")) },
                onEdit   = { id -> navController.navigate(Routes.credentialDetail(id)) },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        // ── Credential detail (add or edit) ───────────────────────────────────
        composable(
            route = Routes.credentialDetail(),
            arguments = listOf(
                navArgument("credentialId") { type = NavType.StringType }
            )
        ) {
            CredentialDetailScreen(onBack = { navController.popBackStack() })
        }

        // ── Settings ──────────────────────────────────────────────────────────
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
