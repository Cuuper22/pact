package app.pact.android.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pact.android.model.SeatView
import app.pact.android.model.TallyEntry
import app.pact.android.ui.EmberAction
import app.pact.android.ui.MoonOutlineAction
import app.pact.android.ui.NightBackground
import app.pact.android.ui.formatRemaining
import app.pact.android.ui.rememberCountdown
import app.pact.android.ui.theme.PactColors

private const val ASK_WINDOW_MS = 60_000f

/**
 * Someone asked the table; you vote. Ember accent (the asking context), large
 * Allow / Not now targets for low light, a live tally, and a 60s countdown.
 * Vote state never relies on color alone — icon + label everywhere.
 */
@Composable
fun AskScreen(
    view: SeatView.Ask,
    onVote: (Boolean) -> Unit,
    onLeave: () -> Unit,
) {
    val remain = rememberCountdown(view.remainMs)
    val progress by animateFloatAsState(
        targetValue = (remain / ASK_WINDOW_MS).coerceIn(0f, 1f),
        animationSpec = tween(200),
        label = "askProgress",
    )

    // Have I already voted? Find my tally entry.
    val myVote = view.tally.firstOrNull { it.seatId == view.me.id }?.vote

    NightBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                "${view.asker} is asking",
                style = MaterialTheme.typography.headlineSmall,
                color = PactColors.Ember,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                view.reason.ifBlank { "No reason" },
                style = MaterialTheme.typography.titleLarge,
                color = PactColors.MoonText,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .height(6.dp),
                color = PactColors.Ember,
                trackColor = PactColors.NightElevated,
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

            if (myVote == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 460.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    MoonOutlineAction(
                        label = "Not now",
                        onClick = { onVote(false) },
                        modifier = Modifier.weight(1f),
                        tint = PactColors.NotNow,
                        icon = Icons.Filled.Close,
                        contentDescription = "Vote not now: deny this request",
                    )
                    EmberAction(
                        label = "Allow",
                        onClick = { onVote(true) },
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Check,
                        contentDescription = "Vote allow: grant this request",
                    )
                }
            } else {
                Text(
                    if (myVote) "You allowed — waiting on the table" else "You said not now",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (myVote) PactColors.Allow else PactColors.NotNow,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun Tally(tally: List<TallyEntry>, meId: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tally.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val name = if (entry.seatId == meId) "${entry.name} (you)" else entry.name
                Text(name, color = PactColors.MoonText, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                VoteBadge(entry.vote)
            }
        }
    }
}

@Composable
private fun VoteBadge(vote: Boolean?) {
    val (label, color) = when (vote) {
        true -> "Allowed" to PactColors.Allow
        false -> "Not now" to PactColors.NotNow
        null -> "Deciding" to PactColors.MoonFaint
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (vote) {
            true -> androidx.compose.material3.Icon(
                Icons.Filled.Check, contentDescription = null, tint = color,
                modifier = Modifier.height(18.dp),
            )
            false -> androidx.compose.material3.Icon(
                Icons.Filled.Close, contentDescription = null, tint = color,
                modifier = Modifier.height(18.dp),
            )
            null -> {}
        }
        Spacer(Modifier.height(0.dp))
        Text("  $label", color = color, style = MaterialTheme.typography.labelMedium)
    }
}
