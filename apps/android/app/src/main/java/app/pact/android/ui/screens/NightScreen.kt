package app.pact.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pact.android.model.SeatView
import app.pact.android.ui.EmberAction
import app.pact.android.ui.MoonOutlineAction
import app.pact.android.ui.NightBackground
import app.pact.android.ui.Roster
import app.pact.android.ui.formatClock
import app.pact.android.ui.rememberPresentClock
import app.pact.android.ui.theme.Flame
import app.pact.android.ui.theme.PactColors

/**
 * The night screen: the only dramatic surface. Near-black indigo, an ambient
 * flame (static ember under reduced motion), the present-time clock read by the
 * whole table, the roster, and the three actions. "Ask the table" opens the
 * reason sheet; the ember accent appears only in that asking context.
 */
@Composable
fun NightScreen(
    view: SeatView.Night,
    reconnecting: Boolean,
    onAsk: (reason: String?) -> Unit,
    onEmergency: () -> Unit,
    onLeave: () -> Unit,
) {
    var showAskSheet by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    val present = rememberPresentClock(view.presentMs)
    val canAsk = view.canAsk ?: true
    val cooldownLeft = view.cooldownMs ?: 0L

    NightBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            Flame(size = 80.dp, lit = true)
            Spacer(Modifier.height(20.dp))

            Text(
                "Present together",
                style = MaterialTheme.typography.labelMedium,
                color = PactColors.MoonMuted,
            )
            Text(
                text = formatClock(present),
                style = MaterialTheme.typography.displayMedium,
                color = PactColors.MoonText,
                modifier = Modifier.semantics {
                    contentDescription = "Present together for ${formatClock(present)}"
                },
            )

            view.banner?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = PactColors.EmberGlow, textAlign = TextAlign.Center)
            }
            view.notice?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = PactColors.MoonMuted, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(28.dp))
            Roster(
                members = view.members,
                modifier = Modifier.widthIn(max = 360.dp),
            )

            if (view.stakes.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                Text(view.stakes, style = MaterialTheme.typography.bodySmall, color = PactColors.MoonFaint, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(36.dp))

            // The ember "Ask the table" — the single warm note, only here.
            EmberAction(
                label = when {
                    canAsk -> "Ask the table"
                    cooldownLeft > 0 -> "Ask again soon"
                    else -> "Ask the table"
                },
                onClick = { showAskSheet = true },
                enabled = canAsk,
                icon = Icons.Filled.LocalFireDepartment,
                contentDescription = "Ask the table to unlock your phone",
                modifier = Modifier.widthIn(max = 420.dp),
            )

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MoonOutlineAction(
                    label = "Emergency",
                    onClick = onEmergency,
                    modifier = Modifier.weight(1f),
                    tint = PactColors.NotNow,
                    icon = Icons.Filled.Call,
                    contentDescription = "Emergency: unlock immediately without the table's vote",
                )
                MoonOutlineAction(
                    label = "Leave",
                    onClick = { confirmLeave = true },
                    modifier = Modifier.weight(1f),
                    tint = PactColors.MoonMuted,
                    icon = Icons.Filled.Logout,
                    contentDescription = "Leave the pact. Always allowed, always visible to the table.",
                )
            }

            if (reconnecting) {
                Spacer(Modifier.height(16.dp))
                Text("Reconnecting…", style = MaterialTheme.typography.labelSmall, color = PactColors.MoonFaint)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showAskSheet) {
        AskReasonSheet(
            onDismiss = { showAskSheet = false },
            onSubmit = { reason ->
                showAskSheet = false
                onAsk(reason)
            },
        )
    }

    if (confirmLeave) {
        LeaveDialog(
            onConfirm = { confirmLeave = false; onLeave() },
            onDismiss = { confirmLeave = false },
        )
    }
}
