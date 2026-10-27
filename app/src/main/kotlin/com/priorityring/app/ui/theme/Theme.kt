package com.priorityring.app.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.priorityring.data.settings.ThemeMode

/** 8 dp grid. Use these instead of ad-hoc numbers. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

/** Purposeful, short motion (180–250 ms per the design brief). */
object Motion {
    const val SHORT_MS = 180
    const val MEDIUM_MS = 250
}

/**
 * True when the user turned on "Remove animations" (system animator scale = 0). Components must
 * then skip decorative motion such as the monitoring pulse.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

private fun Palette.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        // Monochrome: primary actions are "ink" (white on dark, black on light), not a hue.
        primary = text, onPrimary = background,
        primaryContainer = elevated, onPrimaryContainer = text,
        secondary = textSecondary, onSecondary = background,
        secondaryContainer = elevated, onSecondaryContainer = text,
        tertiary = text, onTertiary = background,
        background = background, onBackground = text,
        surface = background, onSurface = text,
        surfaceVariant = surface, onSurfaceVariant = textSecondary,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = elevated,
        surfaceContainerHighest = elevated,
        inverseSurface = text, inverseOnSurface = background,
        outline = divider, outlineVariant = divider,
        error = red, onError = onRed,
        errorContainer = red, onErrorContainer = onRed,
    )
}

// Small radii, per the design brief.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun PriorityRingTheme(
    mode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = if (dark) DarkPalette else LightPalette
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
