package com.priorityring.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.priorityring.app.ui.theme.LocalReduceMotion

/**
 * The "heartbeat" on Home: a solid dot with a ring expanding out of it while monitoring is
 * active. Purely decorative — the status text next to it carries the meaning — so it has no
 * semantics of its own, and it stays still when [active] is false or animations are removed.
 */
@Composable
fun MonitoringPulse(
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val animate = active && !LocalReduceMotion.current
    val progress = if (animate) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
            label = "pulse-progress",
        ).value
    } else 0f

    Canvas(modifier.size(size)) {
        val center = this.center
        val dot = this.size.minDimension * 0.18f
        if (animate) {
            drawCircle(
                color = color.copy(alpha = (1f - progress) * 0.6f),
                radius = dot + (this.size.minDimension / 2 - dot) * progress,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        } else if (active) {
            // Static ring so "active" still reads differently from "inactive" without motion.
            drawCircle(color.copy(alpha = 0.4f), this.size.minDimension * 0.4f, center, style = Stroke(2.dp.toPx()))
        }
        drawCircle(color, dot, center)
    }
}
