package com.priorityring.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * PriorityRing's palette. Mostly monochrome; red is reserved for active alerts, danger and broken
 * states — if something is red, it needs attention.
 *
 * Contrast (WCAG) was checked for text use:
 * - dark: secondary #A0A0A0 on #090909 ≈ 7.6:1, on #232323 ≈ 6.0:1.
 * - #E1261C on #090909 is only ≈ 4.3:1, so red *text* on dark uses [Palette.redText] (≈ 6:1);
 *   #E1261C stays for fills (with white text on it ≈ 4.7:1), icons and the pulse.
 */
@Immutable
data class Palette(
    val background: Color,
    val surface: Color,
    val elevated: Color,
    val text: Color,
    val textSecondary: Color,
    val divider: Color,
    val red: Color,
    val redText: Color,
    val onRed: Color,
    val isDark: Boolean,
)

val DarkPalette = Palette(
    background = Color(0xFF090909),
    surface = Color(0xFF181818),
    elevated = Color(0xFF232323),
    text = Color(0xFFF5F5F3),
    textSecondary = Color(0xFFA0A0A0),
    divider = Color(0xFF343434),
    red = Color(0xFFE1261C),
    redText = Color(0xFFFF5A4F),
    onRed = Color(0xFFFFFFFF),
    isDark = true,
)

val LightPalette = Palette(
    background = Color(0xFFF5F5F3),
    surface = Color(0xFFFFFFFF),
    elevated = Color(0xFFEAEAE7),
    text = Color(0xFF090909),
    textSecondary = Color(0xFF5C5C5C),  // ≈ 6.4:1 on white
    divider = Color(0xFFD4D4D1),
    red = Color(0xFFC21A12),
    redText = Color(0xFFC21A12),        // ≈ 6.1:1 on white
    onRed = Color(0xFFFFFFFF),
    isDark = false,
)

/** Access with `LocalPalette.current` for colours Material's scheme has no slot for. */
val LocalPalette = staticCompositionLocalOf { DarkPalette }
