package app.pact.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pact.android.model.SeatView
import app.pact.android.ui.NightBackground
import app.pact.android.ui.formatRemaining
import app.pact.android.ui.rememberCountdown
import app.pact.android.ui.theme.Flame
import app.pact.android.ui.theme.PactColors

/** Your ask is on the table. Watch the tally fill in; 60s window. */
@Composable
fun WaitingScreen(
    view: SeatView.Waiting,
    onLeave: () -> Unit,
) {
    val remain = rememberCountdown(view.remainMs)
    NightBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Flame(size = 64.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                "Waiting on the table",
                style = MaterialTheme.typography.headlineSmall,
                color = PactColors.Ember,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                view.reason.ifBlank { "No reason" },
                style = MaterialTheme.typography.titleMedium,
                color = PactColors.MoonText,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatRemaining(remain)} left",
                style = MaterialTheme.typography.labelMedium,
                color = PactColors.MoonMuted,
            )

            Spacer(Modifier.height(28.dp))
            Tally(view.tally, meId = view.me.id)

            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onLeave,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
            ) {
                Text("Leave", color = PactColors.MoonMuted)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
