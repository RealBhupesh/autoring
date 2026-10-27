package com.priorityring.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A heading drawn as a 5×7 dot-matrix display with Compose Canvas (our own glyphs — no
 * third-party fonts or assets).
 *
 * Accessibility: the Canvas is invisible to TalkBack, so we attach the real text as the content
 * description and mark it as a heading. Dot size follows the user's font scale, and long text
 * wraps on word boundaries to the available width.
 */
@Composable
fun DotMatrixText(
    text: String,
    modifier: Modifier = Modifier,
    dotSize: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    val density = LocalDensity.current
    val pitchDp = dotSize * density.fontScale * 1.25f
    BoxWithConstraints(modifier.semantics { contentDescription = text; heading() }) {
        val charCols = GLYPH_W + 1
        val maxChars = ((maxWidth / pitchDp).toInt() + 1) / charCols
        val lines = wrap(text.uppercase(), maxChars.coerceAtLeast(1))
        val longest = lines.maxOf { it.length }
        val widthDp = pitchDp * (longest * charCols - 1).coerceAtLeast(1)
        val heightDp = pitchDp * (lines.size * (GLYPH_H + 2) - 2)

        Canvas(Modifier.width(widthDp).height(heightDp)) {
            val pitch = pitchDp.toPx()
            val radius = pitch * 0.4f
            lines.forEachIndexed { lineIdx, line ->
                line.forEachIndexed { charIdx, ch ->
                    val glyph = GLYPHS[ch] ?: GLYPHS.getValue('?')
                    for (row in 0 until GLYPH_H) for (col in 0 until GLYPH_W) {
                        if (glyph[row][col] == '1') {
                            val x = (charIdx * charCols + col) * pitch + pitch / 2
                            val y = (lineIdx * (GLYPH_H + 2) + row) * pitch + pitch / 2
                            drawCircle(color, radius, Offset(x, y))
                        }
                    }
                }
            }
        }
    }
}

private fun wrap(text: String, maxChars: Int): List<String> {
    val lines = mutableListOf<String>()
    var current = ""
    for (word in text.split(' ').filter { it.isNotEmpty() }) {
        // Words longer than a line are hard-split.
        for (piece in word.chunked(maxChars)) {
            current = when {
                current.isEmpty() -> piece
                current.length + 1 + piece.length <= maxChars -> "$current $piece"
                else -> { lines += current; piece }
            }
        }
    }
    if (current.isNotEmpty() || lines.isEmpty()) lines += current
    return lines
}

private const val GLYPH_W = 5
private const val GLYPH_H = 7

// Each glyph is 7 rows of 5 columns, '1' = lit dot.
private val GLYPHS: Map<Char, List<String>> = mapOf(
    'A' to listOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
    'B' to listOf("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
    'C' to listOf("01110", "10001", "10000", "10000", "10000", "10001", "01110"),
    'D' to listOf("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
    'E' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
    'F' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
    'G' to listOf("01110", "10001", "10000", "10111", "10001", "10001", "01111"),
    'H' to listOf("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
    'I' to listOf("01110", "00100", "00100", "00100", "00100", "00100", "01110"),
    'J' to listOf("00111", "00010", "00010", "00010", "00010", "10010", "01100"),
    'K' to listOf("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
    'L' to listOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
    'M' to listOf("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
    'N' to listOf("10001", "10001", "11001", "10101", "10011", "10001", "10001"),
    'O' to listOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
    'P' to listOf("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
    'Q' to listOf("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
    'R' to listOf("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
    'S' to listOf("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
    'T' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
    'U' to listOf("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
    'V' to listOf("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
    'W' to listOf("10001", "10001", "10001", "10101", "10101", "10101", "01010"),
    'X' to listOf("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
    'Y' to listOf("10001", "10001", "10001", "01010", "00100", "00100", "00100"),
    'Z' to listOf("11111", "00001", "00010", "00100", "01000", "10000", "11111"),
    '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
    '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
    '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
    '3' to listOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
    '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
    '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
    '6' to listOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
    '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
    '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
    '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
    ' ' to listOf("00000", "00000", "00000", "00000", "00000", "00000", "00000"),
    '-' to listOf("00000", "00000", "00000", "11111", "00000", "00000", "00000"),
    '.' to listOf("00000", "00000", "00000", "00000", "00000", "01100", "01100"),
    ':' to listOf("00000", "01100", "01100", "00000", "01100", "01100", "00000"),
    '!' to listOf("00100", "00100", "00100", "00100", "00100", "00000", "00100"),
    '?' to listOf("01110", "10001", "00001", "00010", "00100", "00000", "00100"),
    '/' to listOf("00001", "00001", "00010", "00100", "01000", "10000", "10000"),
    '+' to listOf("00000", "00100", "00100", "11111", "00100", "00100", "00000"),
)
