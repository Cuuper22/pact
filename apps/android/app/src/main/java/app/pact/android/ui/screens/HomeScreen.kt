package app.pact.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pact.android.ui.PrimaryAction
import app.pact.android.ui.theme.Flame
import app.pact.android.ui.theme.PactColors

@Composable
fun HomeScreen(
    onStart: () -> Unit,
    onJoin: () -> Unit,
    onPermissions: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Flame(size = 72.dp, lit = true)
            Spacer(Modifier.height(24.dp))
            Text(
                "Pact",
                style = MaterialTheme.typography.displaySmall,
                color = PactColors.Indigo,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Phones down. Everyone in.",
                style = MaterialTheme.typography.bodyLarge,
                color = PactColors.MutedOnLight,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            PrimaryAction(label = "Start a pact", onClick = onStart)
            Spacer(Modifier.height(12.dp))
            PrimaryAction(label = "Join a table", onClick = onJoin)
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onPermissions, modifier = Modifier.fillMaxWidth()) {
                Text("Set up permissions", color = PactColors.Indigo)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "No accounts. The session evaporates after the night.",
                style = MaterialTheme.typography.bodySmall,
                color = PactColors.MutedOnLight,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
