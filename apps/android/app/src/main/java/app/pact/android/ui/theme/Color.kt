package app.pact.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens from IDEA.md (oklch seeds, approximated to sRGB). Defined once.
 *
 * - Primary indigo  ≈ #3B2F8F  (oklch 0.40 0.15 270) — the single pre-pact accent.
 * - Night background ≈ #0D0E16  (oklch 0.12 0.013 275) — the only dramatic surface.
 * - Ember accent     ≈ #E89B5B  (oklch 0.72 0.13 70) — used ONLY when the table
 *   is being asked something. "The one candle on the table."
 * - Light surfaces: pure white.
 */
object PactColors {
    val Indigo = Color(0xFF3B2F8F)
    val IndigoDark = Color(0xFF2A2168)
    val IndigoLight = Color(0xFF5B4FB5)

    val Night = Color(0xFF0D0E16)
    val NightSurface = Color(0xFF15161F)
    val NightElevated = Color(0xFF1D1E29)

    val Ember = Color(0xFFE89B5B)
    val EmberDim = Color(0xFFB5764A)
    val EmberGlow = Color(0xFFF2C293)

    val White = Color(0xFFFFFFFF)
    val OffWhite = Color(0xFFF7F7FA)
    val InkOnLight = Color(0xFF14151C)
    val MutedOnLight = Color(0xFF5A5B66)

    // High-contrast text/glyphs on the night surface (WCAG AA on #0D0E16).
    val MoonText = Color(0xFFEDEDF2)
    val MoonMuted = Color(0xFFA9AAB8)
    val MoonFaint = Color(0xFF6E6F80)

    // Vote semantics — always paired with an icon + label, never color alone.
    val Allow = Color(0xFF6FD08C)
    val NotNow = Color(0xFFE2725B)
}
