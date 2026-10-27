package com.priorityring.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.priorityring.app.R

/*
 * Bundled open-licence fonts (SIL OFL 1.1, licences in /licenses):
 * - Inter for body text, Space Grotesk for headings, JetBrains Mono for small labels.
 * Each file is a *variable* font: one file covers every weight. We declare which weights we use
 * and tell Android which point on the weight axis each one is (supported from API 26).
 */
private fun variable(res: Int, vararg weights: FontWeight) = FontFamily(
    weights.map { w ->
        Font(res, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    },
)

val Inter = variable(R.font.inter_variable, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)
val SpaceGrotesk = variable(R.font.space_grotesk_variable, FontWeight.Medium, FontWeight.Bold)
val JetBrainsMono = variable(R.font.jetbrains_mono_variable, FontWeight.Normal, FontWeight.Medium)

// Sizes are in sp, so they follow the user's font scale (we test at 200%).
val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.04.em),
    labelMedium = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.06.em),
    labelSmall = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.06.em),
)
