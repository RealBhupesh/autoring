package com.priorityring.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.priorityring.app.ui.theme.LocalPalette
import com.priorityring.app.ui.theme.Spacing

/** Monospace uppercase label above a group of content, e.g. "PERMISSIONS". */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(top = Spacing.l, bottom = Spacing.s)
            .semantics { heading() },
    )
}

/** Flat card on the surface colour; the app's basic container. */
@Composable
fun PanelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(Spacing.m), content = content)
    }
}

enum class ChipTone { NEUTRAL, GOOD, DANGER }

/** Small status pill, e.g. GRANTED / MISSING. Text always states the status; colour only reinforces it. */
@Composable
fun StatusChip(text: String, tone: ChipTone, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val (bg, fg) = when (tone) {
        ChipTone.NEUTRAL -> palette.elevated to palette.textSecondary
        ChipTone.GOOD -> palette.elevated to palette.text
        ChipTone.DANGER -> palette.red to palette.onRed
    }
    Box(
        modifier
            .background(bg, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = Spacing.s, vertical = 2.dp),
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
    }
}

/** Screen title row used at the top of each tab. */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(top = Spacing.l, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DotMatrixText(title, Modifier.weight(1f), dotSize = 3.dp)
        trailing()
    }
}
