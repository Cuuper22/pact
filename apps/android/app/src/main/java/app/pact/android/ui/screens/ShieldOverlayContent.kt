package app.pact.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pact.android.ui.EmberAction
import app.pact.android.ui.MoonOutlineAction
import app.pact.android.ui.NightBackground
import app.pact.android.ui.theme.CompositionLocalReducedMotion
import app.pact.android.ui.theme.Flame
import app.pact.android.ui.theme.PactColors

/**
 * The overlay shield reproduces the night screen's look: it replaces the
 * temptation surface with the table. "Ask the table" turns the very app you
 * tried to open into the request surface; "Open Pact" returns to the app.
 *
 * Runs inside a WindowManager overlay (no Activity), so it takes its inputs as
 * plain parameters rather than reading a ViewModel.
 */
@Composable
fun ShieldOverlayContent(
    reducedMotion: Boolean,
    onAsk: () -> Unit,
    onOpenApp: () -> Unit,
) {
    CompositionLocalReducedMotion(reducedMotion) {
        NightBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Flame(size = 88.dp, reducedMotion = reducedMotion, lit = true)
                Spacer(Modifier.height(28.dp))
                Text(
                    "Phones down. Everyone in.",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PactColors.MoonText,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "This phone is in a pact. The table is waiting.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PactColors.MoonMuted,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(36.dp))
                EmberAction(
                    label = "Ask the table",
                    onClick = onAsk,
                    icon = Icons.Filled.LocalFireDepartment,
                    contentDescription = "Ask the table to unlock your phone",
                    modifier = Modifier.widthIn(max = 420.dp),
                )
                Spacer(Modifier.height(14.dp))
                MoonOutlineAction(
                    label = "Open Pact",
                    onClick = onOpenApp,
                    modifier = Modifier.widthIn(max = 420.dp),
                    contentDescription = "Open the Pact app",
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "Emergency calls are never blocked.",
                    style = MaterialTheme.typography.labelSmall,
                    color = PactColors.MoonFaint,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
