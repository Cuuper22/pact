package app.pact.android.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Pre-pact surfaces are pure white with one indigo primary. The night surface
 * has its own dedicated palette applied directly by NightScreen / the shield,
 * not through this scheme (it is intentionally the only dramatic surface).
 *
 * Weights 400/500 only, per the design direction.
 */

private val LightScheme = lightColorScheme(
    primary = PactColors.Indigo,
    onPrimary = PactColors.White,
    primaryContainer = PactColors.IndigoLight,
    onPrimaryContainer = PactColors.White,
    secondary = PactColors.Indigo,
    onSecondary = PactColors.White,
    background = PactColors.White,
    onBackground = PactColors.InkOnLight,
    surface = PactColors.White,
    onSurface = PactColors.InkOnLight,
    surfaceVariant = PactColors.OffWhite,
    onSurfaceVariant = PactColors.MutedOnLight,
    outline = PactColors.MutedOnLight,
    error = PactColors.NotNow,
)

// A dark scheme so system-dark users get a coherent (still restrained) look on
// the pre-pact surfaces. The night/shield screens override this anyway.
private val DarkScheme = darkColorScheme(
    primary = PactColors.IndigoLight,
    onPrimary = PactColors.White,
    primaryContainer = PactColors.IndigoDark,
    onPrimaryContainer = PactColors.MoonText,
    secondary = PactColors.IndigoLight,
    onSecondary = PactColors.White,
    background = PactColors.Night,
    onBackground = PactColors.MoonText,
    surface = PactColors.NightSurface,
    onSurface = PactColors.MoonText,
    surfaceVariant = PactColors.NightElevated,
    onSurfaceVariant = PactColors.MoonMuted,
    outline = PactColors.MoonFaint,
    error = PactColors.NotNow,
)

private val PactTypography = Typography().let { base ->
    fun TextStyle.w(weight: FontWeight) = copy(fontWeight = weight)
    Typography(
        displayLarge = base.displayLarge.w(FontWeight.W400),
        displayMedium = base.displayMedium.w(FontWeight.W400),
        displaySmall = base.displaySmall.w(FontWeight.W400),
        headlineLarge = base.headlineLarge.w(FontWeight.W400),
        headlineMedium = base.headlineMedium.w(FontWeight.W500),
        headlineSmall = base.headlineSmall.w(FontWeight.W500),
        titleLarge = base.titleLarge.w(FontWeight.W500),
        titleMedium = base.titleMedium.w(FontWeight.W500),
        titleSmall = base.titleSmall.w(FontWeight.W500),
        bodyLarge = base.bodyLarge.w(FontWeight.W400).copy(fontSize = 16.sp),
        bodyMedium = base.bodyMedium.w(FontWeight.W400),
        bodySmall = base.bodySmall.w(FontWeight.W400),
        labelLarge = base.labelLarge.w(FontWeight.W500),
        labelMedium = base.labelMedium.w(FontWeight.W500),
        labelSmall = base.labelSmall.w(FontWeight.W500),
    )
}

/** Exposes whether the OS "remove animations" / reduced-motion is on. */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Convenience provider for contexts that build their own CompositionLocals
 *  (e.g. the WindowManager overlay, which has no PactTheme above it). */
@Composable
fun CompositionLocalReducedMotion(value: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReducedMotion provides value, content = content)
}

@Composable
fun PactTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) { isReducedMotion(context) }
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = PactTypography,
            content = content,
        )
    }
}

/**
 * Reads the system "Remove animations" accessibility setting. When on, the
 * flame becomes a static ember and transitions crossfade.
 */
fun isReducedMotion(context: Context): Boolean {
    return try {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        scale == 0f
    } catch (_: Throwable) {
        false
    }
}
