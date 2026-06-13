package app.pact.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pact.android.model.SeatView
import app.pact.android.ui.PrimaryAction
import app.pact.android.ui.QrImage
import app.pact.android.ui.Roster
import app.pact.android.ui.ScreenTitle
import app.pact.android.ui.theme.PactColors

@Composable
fun LobbyHostScreen(
    view: SeatView.LobbyHost,
    reconnecting: Boolean,
    onLock: () -> Unit,
    onLeave: () -> Unit,
) {
    val joinLink = "pact://join?code=${view.code}"
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            ScreenTitle("Everyone scans in", "When the last person is in, lock the table together.")
            if (reconnecting) {
                Spacer(Modifier.height(8.dp))
                Text("Reconnecting…", style = MaterialTheme.typography.labelMedium, color = PactColors.MutedOnLight)
            }
            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = PactColors.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .aspectRatio(1f),
                    ) {
                        QrImage(content = joinLink, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        view.code,
                        style = MaterialTheme.typography.headlineMedium,
                        color = PactColors.Indigo,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Table code",
                        style = MaterialTheme.typography.labelMedium,
                        color = PactColors.MutedOnLight,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "At the table (${view.members.size})",
                style = MaterialTheme.typography.titleMedium,
                color = PactColors.InkOnLight,
            )
            Spacer(Modifier.height(10.dp))
            Roster(members = view.members, onLight = true)

            if (view.stakes.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                Text("Stakes", style = MaterialTheme.typography.labelMedium, color = PactColors.MutedOnLight)
                Text(view.stakes, style = MaterialTheme.typography.bodyLarge, color = PactColors.InkOnLight)
            }

            Spacer(Modifier.height(28.dp))
            PrimaryAction(
                label = if (view.canLock) "Lock the table" else "Waiting for the table…",
                onClick = onLock,
                enabled = view.canLock,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                Text("Leave", color = PactColors.MutedOnLight)
            }
        }
    }
}
