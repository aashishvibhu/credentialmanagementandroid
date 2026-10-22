package com.github.aashishvibhu.credentialmanagement.ui.navigation

import android.net.Uri
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
import com.github.aashishvibhu.credentialmanagement.data.vault.VaultStatus
import com.github.aashishvibhu.credentialmanagement.ui.auth.AuthViewModel
import com.github.aashishvibhu.credentialmanagement.ui.auth.SignInScreen
import com.github.aashishvibhu.credentialmanagement.ui.biometric.BiometricLockScreen
import com.github.aashishvibhu.credentialmanagement.ui.biometric.LockViewModel
import com.github.aashishvibhu.credentialmanagement.ui.credentialdetail.CredentialDetailScreen
import com.github.aashishvibhu.credentialmanagement.ui.credentiallist.CredentialListScreen
import com.github.aashishvibhu.credentialmanagement.ui.settings.SettingsScreen
import com.github.aashishvibhu.credentialmanagement.ui.settings.SettingsViewModel
import com.github.aashishvibhu.credentialmanagement.ui.vault.ChangeMode
import com.github.aashishvibhu.credentialmanagement.ui.vault.ChangePasswordScreen
import com.github.aashishvibhu.credentialmanagement.ui.vault.CreateMode
import com.github.aashishvibhu.credentialmanagement.ui.vault.CreatePasswordScreen
import com.github.aashishvibhu.credentialmanagement.ui.vault.RecoveryKeyScreen
import com.github.aashishvibhu.credentialmanagement.ui.vault.UnlockMode
import com.github.aashishvibhu.credentialmanagement.ui.vault.UnlockResult
import com.github.aashishvibhu.credentialmanagement.ui.vault.UnlockScreen
import com.github.aashishvibhu.credentialmanagement.ui.vault.VaultGateScreen
import com.github.aashishvibhu.credentialmanagement.ui.vault.VaultUnreadableScreen

object Routes {
    const val SIGN_IN           = "sign_in"
    const val VAULT_GATE        = "vault_gate"
    const val BIOMETRIC_LOCK    = "biometric_lock"
    const val CREDENTIAL_LIST   = "credential_list"
    const val CREDENTIAL_DETAIL = "credential_detail"
    const val SETTINGS          = "settings"

    // Master-password flows; {mode} is the name of the screen's mode enum
    const val CREATE_PASSWORD   = "create_password"
    const val RECOVERY_KEY      = "recovery_key"
    const val UNLOCK            = "unlock"
    const val VAULT_UNREADABLE  = "vault_unreadable"
    const val CHANGE_PASSWORD   = "change_password"

    const val RECOVERY_SETUP    = "SETUP"
    const val RECOVERY_SETTINGS = "SETTINGS"

    /** Detail route; the optional service pre-fills "Add another account" from a group row. */
    const val CREDENTIAL_DETAIL_PATTERN = "$CREDENTIAL_DETAIL/{credentialId}?service={service}"

    fun credentialDetail(id: String, service: String? = null) =
        if (service == null) "$CREDENTIAL_DETAIL/$id"
        else "$CREDENTIAL_DETAIL/$id?service=${Uri.encode(service)}"

    fun withMode(base: String, mode: Any) = "$base/$mode"

    /** Screens reachable while locked: signing in, unlocking, and first-time vault setup. */
    fun allowedWhileLocked(base: String, mode: String?): Boolean = when (base) {
        SIGN_IN, VAULT_GATE, BIOMETRIC_LOCK, CREATE_PASSWORD, UNLOCK, VAULT_UNREADABLE -> true
        RECOVERY_KEY    -> mode == RECOVERY_SETUP
        CHANGE_PASSWORD -> mode == ChangeMode.RESET.name
        else            -> false
    }
}

/** Go to [route] and drop everything behind it, so Back can't return to a setup or lock screen. */
private fun NavHostController.replaceAll(route: String) =
    navigate(route) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
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
            navController.replaceAll(Routes.SIGN_IN)
        }
        prevAuthState = authState
    }

    // Re-lock: when the vault locks (background timeout or "Lock now") while signed in, redirect to
    // the lock screen. The full back stack is cleared so no protected screen lingers behind it.
    val lockViewModel: LockViewModel = hiltViewModel()
    val isLocked by lockViewModel.isLocked.collectAsState()
    val currentEntry = navController.currentBackStackEntryAsState().value
    val currentBase = currentEntry?.destination?.route?.substringBefore('/')
    val currentMode = currentEntry?.arguments?.getString("mode")
    LaunchedEffect(isLocked, currentBase, currentMode, authState) {
        if (isLocked &&
            authState is AuthState.SignedIn &&
            currentBase != null &&
            !Routes.allowedWhileLocked(currentBase, currentMode)
        ) {
            navController.replaceAll(Routes.BIOMETRIC_LOCK)
        }
    }

    fun openVault() {
        lockViewModel.unlock()
        navController.replaceAll(Routes.CREDENTIAL_LIST)
    }

    val modeArg = listOf(navArgument("mode") { type = NavType.StringType })

    NavHost(
        navController = navController,
        startDestination = Routes.SIGN_IN
    ) {

        // ── Sign-in ───────────────────────────────────────────────────────────
        composable(Routes.SIGN_IN) {
            SignInScreen(
                viewModel = authViewModel,
                onSignedIn = { navController.replaceAll(Routes.VAULT_GATE) }
            )
        }

        // ── Vault gate: which master-password step comes next ─────────────────
        composable(Routes.VAULT_GATE) {
            val settings: SettingsViewModel = hiltViewModel()
            VaultGateScreen(
                onResolved = { status ->
                    navController.replaceAll(
                        when (status) {
                            VaultStatus.Ready -> Routes.BIOMETRIC_LOCK
                            VaultStatus.NoVault -> Routes.withMode(Routes.CREATE_PASSWORD, CreateMode.NEW)
                            VaultStatus.NeedsPassword -> Routes.withMode(Routes.UNLOCK, UnlockMode.DRIVE)
                            is VaultStatus.LegacyReadable -> Routes.withMode(Routes.CREATE_PASSWORD, CreateMode.UPGRADE)
                            VaultStatus.LegacyUnreadable -> Routes.VAULT_UNREADABLE
                        }
                    )
                },
                onSignOut = settings::signOut
            )
        }

        composable(Routes.withMode(Routes.CREATE_PASSWORD, "{mode}"), arguments = modeArg) {
            CreatePasswordScreen(
                onCreated = { navController.replaceAll(Routes.withMode(Routes.RECOVERY_KEY, Routes.RECOVERY_SETUP)) }
            )
        }

        composable(Routes.withMode(Routes.RECOVERY_KEY, "{mode}"), arguments = modeArg) { entry ->
            val firstTime = entry.arguments?.getString("mode") == Routes.RECOVERY_SETUP
            RecoveryKeyScreen(
                firstTime = firstTime,
                onDone = { if (firstTime) openVault() else navController.popBackStack() }
            )
        }

        composable(Routes.withMode(Routes.UNLOCK, "{mode}"), arguments = modeArg) {
            UnlockScreen(
                onUnlocked = { result ->
                    when (result) {
                        UnlockResult.OPENED -> openVault()
                        UnlockResult.RECOVERED ->
                            navController.replaceAll(Routes.withMode(Routes.CHANGE_PASSWORD, ChangeMode.RESET))
                    }
                }
            )
        }

        composable(Routes.VAULT_UNREADABLE) {
            val settings: SettingsViewModel = hiltViewModel()
            VaultUnreadableScreen(
                onStartNewVault = {
                    navController.replaceAll(Routes.withMode(Routes.CREATE_PASSWORD, CreateMode.REPLACE))
                },
                onSignOut = settings::signOut
            )
        }

        composable(Routes.withMode(Routes.CHANGE_PASSWORD, "{mode}"), arguments = modeArg) { entry ->
            val reset = entry.arguments?.getString("mode") == ChangeMode.RESET.name
            ChangePasswordScreen(
                onDone = { if (reset) openVault() else navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }

        // ── Lock ──────────────────────────────────────────────────────────────
        composable(Routes.BIOMETRIC_LOCK) {
            BiometricLockScreen(
                onAuthenticated = { navController.replaceAll(Routes.CREDENTIAL_LIST) },
                onUseMasterPassword = {
                    navController.navigate(Routes.withMode(Routes.UNLOCK, UnlockMode.LOCAL)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // ── Credential list ───────────────────────────────────────────────────
        composable(Routes.CREDENTIAL_LIST) {
            CredentialListScreen(
                onAddNew = { service -> navController.navigate(Routes.credentialDetail("new", service)) },
                onEdit   = { id -> navController.navigate(Routes.credentialDetail(id)) },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        // ── Credential detail (add or edit) ───────────────────────────────────
        composable(
            route = Routes.CREDENTIAL_DETAIL_PATTERN,
            arguments = listOf(
                navArgument("credentialId") { type = NavType.StringType },
                navArgument("service") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            CredentialDetailScreen(onBack = { navController.popBackStack() })
        }

        // ── Settings ──────────────────────────────────────────────────────────
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onChangePassword = {
                    navController.navigate(Routes.withMode(Routes.CHANGE_PASSWORD, ChangeMode.CHANGE))
                },
                onRecoveryKeyReady = {
                    navController.navigate(Routes.withMode(Routes.RECOVERY_KEY, Routes.RECOVERY_SETTINGS))
                }
            )
        }
    }
}
