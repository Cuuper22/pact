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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pact.android.model.SeatView
import app.pact.android.ui.NightBackground
import app.pact.android.ui.formatRemaining
import app.pact.android.ui.rememberCountdown
import app.pact.android.ui.theme.PactColors

/**
 * You're unshielded, counting down to relock. The countdown is the whole point:
 * the table can see it too. Emergency passes are labelled distinctly.
 */
@Composable
fun PassScreen(
    view: SeatView.Pass,
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
            Spacer(Modifier.height(48.dp))
            Text(
                if (view.emergency) "Emergency pass" else "Your pass",
                style = MaterialTheme.typography.titleMedium,
                color = if (view.emergency) PactColors.NotNow else PactColors.Ember,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatRemaining(remain),
                style = MaterialTheme.typography.displayLarge,
                color = PactColors.MoonText,
                modifier = Modifier.semantics {
                    contentDescription = "Pass relocks in ${formatRemaining(remain)}"
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Phone relocks automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = PactColors.MoonMuted,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onLeave,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
            ) {
                Text("Leave the pact", color = PactColors.MoonMuted)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
