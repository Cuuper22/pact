package app.pact.android.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import app.pact.android.ui.FlowState
import app.pact.android.ui.PrimaryAction
import app.pact.android.ui.ScreenTitle
import app.pact.android.ui.theme.PactColors

/**
 * Join by scanning the host's QR (zxing-android-embedded) or typing the table
 * code. The QR encodes pact://join?code=TBL-XXXX; we extract the code from it.
 */
@Composable
fun JoinScreen(
    prefillCode: String?,
    flowState: FlowState,
    onJoin: (code: String, name: String) -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit,
) {
    var code by remember { mutableStateOf(prefillCode.orEmpty()) }
    var name by remember { mutableStateOf("") }
    val working = flowState is FlowState.Working

    LaunchedEffect(prefillCode) {
        if (!prefillCode.isNullOrBlank()) code = prefillCode
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (!contents.isNullOrBlank()) {
            code = parseJoinCode(contents) ?: contents.trim()
            onClearError()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            ScreenTitle("Join a table", "Scan the host's QR or type the table code.")
            Spacer(Modifier.height(24.dp))

            OutlinedButton(
                onClick = {
                    scanLauncher.launch(
                        ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("Point at the host's Pact QR")
                            setBeepEnabled(false)
                            setOrientationLocked(false)
                        },
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text("  Scan QR")
            }

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase(); onClearError() },
                label = { Text("Table code") },
                placeholder = { Text("TBL-K7QP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next,
                ),
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; onClearError() },
                label = { Text("Your first name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Done,
                ),
            )

            if (flowState is FlowState.Error) {
                Spacer(Modifier.height(16.dp))
                Text(flowState.message, color = PactColors.NotNow, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(28.dp))
            PrimaryAction(
                label = if (working) "Joining…" else "Join",
                onClick = { onJoin(code, name) },
                enabled = code.isNotBlank() && name.isNotBlank() && !working,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back", color = PactColors.Indigo)
            }
        }
    }
}

/** Extracts TBL-XXXX from a pact://join?code=... link, else null. */
private fun parseJoinCode(scanned: String): String? {
    return try {
        val uri = Uri.parse(scanned.trim())
        if (uri.scheme == "pact") uri.getQueryParameter("code") else null
    } catch (_: Throwable) {
        null
    }
}
