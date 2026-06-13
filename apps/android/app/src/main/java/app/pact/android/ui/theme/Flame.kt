package app.pact.android.ui.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The small ambient flame on the night screen — the single slow-motion exception
 * to the 150–250ms rule. With reduced motion, it renders as a static ember.
 *
 * The flame is a custom-drawn glyph; no asset, scales crisply, and stays cheap.
 */
@Composable
fun Flame(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    reducedMotion: Boolean = LocalReducedMotion.current,
    /** When true the flame is "lit"; when the pact breaks it can be dimmed. */
    lit: Boolean = true,
) {
    val flicker: Float
    val sway: Float
    if (reducedMotion || !lit) {
        flicker = 1f
        sway = 0f
    } else {
        val transition = rememberInfiniteTransition(label = "flame")
        val f by transition.animateFloat(
            initialValue = 0.86f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(1700),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "flicker",
        )
        val s by transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2300),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "sway",
        )
        flicker = f
        sway = s
    }

    Canvas(modifier = modifier.size(size)) {
        drawFlame(flicker = flicker, sway = sway, lit = lit)
    }
}

private fun DrawScope.drawFlame(flicker: Float, sway: Float, lit: Boolean) {
    val w = this.size.width
    val h = this.size.height
    val cx = w / 2f + sway * (w * 0.03f)
    val baseY = h * 0.92f
    val tipY = h * (0.10f) - (flicker - 1f) * h * 0.12f
    val bodyWidth = w * 0.42f * (0.92f + (flicker - 1f) * 0.5f)

    // Outer flame body (ember gradient).
    val outer = Path().apply {
        moveTo(cx, tipY)
        cubicTo(
            cx + bodyWidth, h * 0.42f,
            cx + bodyWidth * 0.7f, baseY,
            cx, baseY,
        )
        cubicTo(
            cx - bodyWidth * 0.7f, baseY,
            cx - bodyWidth, h * 0.42f,
            cx, tipY,
        )
        close()
    }
    val outerBrush = Brush.verticalGradient(
        colors = if (lit) {
            listOf(PactColors.EmberGlow, PactColors.Ember, PactColors.EmberDim)
        } else {
            listOf(PactColors.MoonFaint, PactColors.EmberDim.copy(alpha = 0.5f))
        },
        startY = tipY,
        endY = baseY,
    )
    drawPath(outer, brush = outerBrush)

    if (lit) {
        // Inner glow.
        val innerWidth = bodyWidth * 0.5f
        val inner = Path().apply {
            val innerTip = h * 0.40f
            moveTo(cx, innerTip)
            cubicTo(
                cx + innerWidth, h * 0.60f,
                cx + innerWidth * 0.6f, baseY * 0.99f,
                cx, baseY * 0.99f,
            )
            cubicTo(
                cx - innerWidth * 0.6f, baseY * 0.99f,
                cx - innerWidth, h * 0.60f,
                cx, innerTip,
            )
            close()
        }
        drawPath(
            inner,
            brush = Brush.radialGradient(
                colors = listOf(PactColors.White.copy(alpha = 0.85f), PactColors.EmberGlow),
                center = Offset(cx, baseY * 0.8f),
                radius = innerWidth * 2f,
            ),
        )
    }
}
