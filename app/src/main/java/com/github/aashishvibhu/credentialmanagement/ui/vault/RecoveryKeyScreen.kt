package com.github.aashishvibhu.credentialmanagement.ui.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Support
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.github.aashishvibhu.credentialmanagement.data.auth.AuthRepository
import com.github.aashishvibhu.credentialmanagement.data.vault.VaultAccess
import com.github.aashishvibhu.credentialmanagement.util.writeRecoveryKeyPdf
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RecoveryKeyViewModel @Inject constructor(
    private val vaultAccess: VaultAccess,
    authRepository: AuthRepository
) : ViewModel() {

    /** Null only if the app was killed before the key was shown; it is never stored. */
    val recoveryKey: String? = vaultAccess.takeRecoveryKey()
    val account: String? = authRepository.getSignedInAccount()?.email

    fun finish() = vaultAccess.clearRecoveryKey()
}

/**
 * Shows a new recovery key once. [firstTime] is the end of vault setup ("Open my vault");
 * otherwise it was replaced from Settings ("Done").
 */
@Composable
fun RecoveryKeyScreen(
    firstTime: Boolean,
    onDone: () -> Unit,
    viewModel: RecoveryKeyViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(false) }
    val key = viewModel.recoveryKey

    val savePdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null && key != null) {
            runCatching { writeRecoveryKeyPdf(context, uri, key, viewModel.account) }
                .onSuccess { saved = true; toast(context, "Recovery key saved") }
                .onFailure { toast(context, "Couldn't save the file") }
        }
    }

    fun finish() { viewModel.finish(); onDone() }

    VaultPage(
        icon = Icons.Outlined.Support,
        title = "Save your recovery key",
        body = "If you forget your master password, this key is the only way back in. " +
            "Store it somewhere safe, outside this phone.",
        bottom = {
            PillButton(
                if (firstTime) "Open my vault" else "Done",
                onClick = ::finish,
                enabled = saved || key == null
            )
        }
    ) {
        if (key == null) {
            Text(
                "This key can't be shown again. Create a new one in Settings → Security.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            return@VaultPage
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            SelectionContainer {
                Text(
                    // Two lines of three groups read better than one long line
                    key.split("-").chunked(3).joinToString("\n") { it.joinToString("-") },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    lineHeight = 32.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { copySensitive(context, key); toast(context, "Recovery key copied") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.padding(end = 8.dp))
                Text("Copy")
            }
            OutlinedButton(
                onClick = { savePdf.launch("vault-recovery-key.pdf") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Download, null, modifier = Modifier.padding(end = 8.dp))
                Text("Save PDF")
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { saved = !saved }
        ) {
            Checkbox(checked = saved, onCheckedChange = { saved = it })
            Text("I've saved my recovery key", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Copies text marked as sensitive, so Android 13+ hides it from the clipboard preview. */
private fun copySensitive(context: Context, text: String) {
    val clip = ClipData.newPlainText("Recovery key", text)
    clip.description.extras = PersistableBundle().apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}

private fun toast(context: Context, text: String) =
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
