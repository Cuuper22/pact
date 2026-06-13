package app.pact.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.pact.android.ui.FlowState
import app.pact.android.ui.PrimaryAction
import app.pact.android.ui.ScreenTitle
import app.pact.android.ui.theme.PactColors

@Composable
fun StartScreen(
    flowState: FlowState,
    onCreate: (name: String, passMinutes: Int, stakes: String) -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var stakes by remember { mutableStateOf("") }
    var passMinutes by remember { mutableIntStateOf(5) }
    val working = flowState is FlowState.Working

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            ScreenTitle("Start a pact", "You'll get a code and a QR for everyone to scan.")
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; onClearError() },
                label = { Text("Your first name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))

            Text("Pass length", style = MaterialTheme.typography.titleMedium, color = PactColors.InkOnLight)
            Spacer(Modifier.height(4.dp))
            Text(
                "How long a granted pass lasts before the phone relocks.",
                style = MaterialTheme.typography.bodySmall,
                color = PactColors.MutedOnLight,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(2, 5, 10).forEach { m ->
                    FilterChip(
                        selected = passMinutes == m,
                        onClick = { passMinutes = m },
                        label = { Text("$m min") },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = stakes,
                onValueChange = { stakes = it; onClearError() },
                label = { Text("Stakes (optional)") },
                placeholder = { Text("First break buys dessert") },
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
                label = if (working) "Starting…" else "Start",
                onClick = { onCreate(name, passMinutes, stakes) },
                enabled = name.isNotBlank() && !working,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back", color = PactColors.Indigo)
            }
        }
    }
}
