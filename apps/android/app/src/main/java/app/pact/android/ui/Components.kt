package app.pact.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pact.android.model.MemberView
import app.pact.android.ui.theme.PactColors

/** Minimum 56dp tap target for the critical low-light actions. */
val LargeTargetHeight = 64.dp

/** A full-bleed, dark indigo "night sky" backdrop shared by night + shield. */
@Composable
fun NightBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(PactColors.Night, Color(0xFF0A0A12)),
                ),
            ),
    ) {
        content()
    }
}

/** A large primary action, used on light surfaces. */
@Composable
fun PrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LargeTargetHeight),
        shape = RoundedCornerShape(16.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

/** A warm ember action — only ever shown when the table is being asked. */
@Composable
fun EmberAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentDescription: String? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LargeTargetHeight)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PactColors.Ember,
            contentColor = PactColors.Night,
            disabledContainerColor = PactColors.EmberDim.copy(alpha = 0.4f),
            disabledContentColor = PactColors.MoonMuted,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

/** A quiet, outlined action on the night surface (Leave / Emergency framing). */
@Composable
fun MoonOutlineAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = PactColors.MoonMuted,
    icon: ImageVector? = null,
    contentDescription: String? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.4f)),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** A compact roster of first names, readable across the table. */
@Composable
fun Roster(
    members: List<MemberView>,
    modifier: Modifier = Modifier,
    onLight: Boolean = false,
    leftIds: Set<String> = emptySet(),
) {
    val nameColor = if (onLight) PactColors.InkOnLight else PactColors.MoonText
    val mutedColor = if (onLight) PactColors.MutedOnLight else PactColors.MoonMuted
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        members.forEach { m ->
            val left = m.id in leftIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = buildString {
                            append(m.name)
                            if (m.host) append(", host")
                            if (left) append(", left the pact")
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = m.name,
                    color = if (left) mutedColor else nameColor,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (m.host) {
                    Spacer(Modifier.width(8.dp))
                    Text("host", color = mutedColor, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.weight(1f))
                if (left) {
                    Text("left", color = mutedColor, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Centered column scaffold for the simple light onboarding/flow screens. */
@Composable
fun LightScreenScaffold(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = { content() },
        )
    }
}

@Composable
fun ScreenTitle(text: String, subtitle: String? = null, onLight: Boolean = true) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.headlineMedium,
            color = if (onLight) PactColors.InkOnLight else PactColors.MoonText,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (onLight) PactColors.MutedOnLight else PactColors.MoonMuted,
                textAlign = TextAlign.Start,
            )
        }
    }
}
