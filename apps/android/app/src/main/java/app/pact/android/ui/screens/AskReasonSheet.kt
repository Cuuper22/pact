package app.pact.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pact.android.ui.EmberAction
import app.pact.android.ui.theme.PactColors

private val REASONS = listOf(
    "Expecting a call",
    "Need to pay",
    "Directions",
    "Kids",
    "Work",
    "No reason",
)

/**
 * Reason picker for "Ask the table". Chips plus a free-text field. "No reason"
 * is a valid choice; the table can judge it. Submitting sends the ask over the
 * live socket; everyone else gets it on-screen and as a push.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AskReasonSheet(
    onDismiss: () -> Unit,
    onSubmit: (reason: String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var selected by remember { mutableStateOf<String?>(null) }
    var custom by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PactColors.NightElevated,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                "Ask the table",
                style = MaterialTheme.typography.titleLarge,
                color = PactColors.MoonText,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Everyone has 60 seconds to answer. All yes and you get a pass.",
                style = MaterialTheme.typography.bodyMedium,
                color = PactColors.MoonMuted,
            )
            Spacer(Modifier.height(16.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REASONS.forEach { r ->
                    FilterChip(
                        selected = selected == r,
                        onClick = {
                            selected = if (selected == r) null else r
                            custom = ""
                        },
                        label = { Text(r) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it; if (it.isNotBlank()) selected = null },
                label = { Text("Or type a reason") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            EmberAction(
                label = "Send to the table",
                onClick = {
                    val reason = when {
                        custom.isNotBlank() -> custom.trim()
                        selected == "No reason" -> null
                        selected != null -> selected
                        else -> null
                    }
                    onSubmit(reason)
                },
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = PactColors.MoonMuted)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Two-tap leave confirmation. Leaving is never disabled, just confirmed. */
@Composable
fun LeaveDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leave the pact?") },
        text = {
            Text(
                "Your flame goes out, the recap names you, and your phone unlocks. " +
                    "The table will see that you left.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Leave", color = PactColors.NotNow)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Stay") }
        },
    )
}
