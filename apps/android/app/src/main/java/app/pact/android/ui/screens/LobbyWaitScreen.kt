package app.pact.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pact.android.model.SeatView
import app.pact.android.ui.Roster
import app.pact.android.ui.ScreenTitle
import app.pact.android.ui.theme.PactColors

@Composable
fun LobbyWaitScreen(
    view: SeatView.LobbyWait,
    reconnecting: Boolean,
    onLeave: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            ScreenTitle("You're in", "Waiting for the host to lock the table.")
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(color = PactColors.Indigo)
            Spacer(Modifier.height(24.dp))
            Text(
                "At the table (${view.members.size})",
                style = MaterialTheme.typography.titleMedium,
                color = PactColors.InkOnLight,
            )
            Spacer(Modifier.height(10.dp))
            Roster(members = view.members, onLight = true)

            if (reconnecting) {
                Spacer(Modifier.height(16.dp))
                Text("Reconnecting…", style = MaterialTheme.typography.labelMedium, color = PactColors.MutedOnLight)
            }

            Spacer(Modifier.weight(1f))
            TextButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                Text("Leave", color = PactColors.MutedOnLight)
            }
        }
    }
}
