package app.pact.android.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.pact.android.model.Recap
import app.pact.android.model.SeatView
import app.pact.android.ui.PrimaryAction
import app.pact.android.ui.ScreenTitle
import app.pact.android.ui.formatClock
import app.pact.android.ui.theme.PactColors

/**
 * The recap card: the share moment. Time present, asks/granted/refused, who
 * broke it, stakes. A share intent drops it into the group chat the table was
 * avoiding — the growth loop.
 */
@Composable
fun BrokenScreen(
    view: SeatView.Broken,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val recap = view.recap

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            ScreenTitle("The pact's done", "Here's how the table did.")
            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = PactColors.OffWhite),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    StatRow("Present together", formatClock(recap.presentMs))
                    StatRow("Asks made", recap.asks.toString())
                    StatRow("Granted", recap.granted.toString())
                    StatRow("Refused", recap.denied.toString())
                    if (recap.stakes.isNotBlank()) {
                        StatRow("Stakes", recap.stakes)
                    }
                    StatRow(
                        "Broke it",
                        recap.brokenBy ?: "Nobody — clean night",
                        highlight = recap.brokenBy != null,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            PrimaryAction(
                label = "Share to the group",
                icon = Icons.Filled.Share,
                onClick = { shareRecap(context, recap) },
            )
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.TextButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Done", color = PactColors.Indigo)
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = PactColors.MutedOnLight)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlight) PactColors.Ember else PactColors.InkOnLight,
        )
    }
}

private fun shareRecap(context: android.content.Context, recap: Recap) {
    val text = buildString {
        append("Pact recap — we stayed present for ${formatClock(recap.presentMs)}.\n")
        append("Asks: ${recap.asks} · granted ${recap.granted} · refused ${recap.denied}\n")
        if (recap.stakes.isNotBlank()) append("Stakes: ${recap.stakes}\n")
        append(
            recap.brokenBy?.let { "$it broke it. 🍰" } ?: "Nobody broke it. Clean night.",
        )
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share recap").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
