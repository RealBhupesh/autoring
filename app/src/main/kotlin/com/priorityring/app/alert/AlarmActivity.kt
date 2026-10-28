package com.priorityring.app.alert

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.priorityring.app.ui.components.DotMatrixText
import com.priorityring.app.ui.components.MonitoringPulse
import com.priorityring.app.ui.theme.LocalPalette
import com.priorityring.app.ui.theme.PriorityRingTheme
import com.priorityring.app.ui.theme.Spacing
import com.priorityring.engine.alert.AlertController
import com.priorityring.engine.alert.AlertPhase
import com.priorityring.engine.alert.AlertState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The full-screen alarm: who/what, how long it's been ringing, a huge STOP and a SNOOZE. Shown over
 * the lock screen (without unlocking it) when Android allows full-screen intents, or when the user
 * taps the alarm notification. Closes itself as soon as the alert ends, whoever stopped it.
 */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject lateinit var alerts: AlertController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        setContent {
            PriorityRingTheme {
                val state by alerts.state.collectAsStateWithLifecycle()
                LaunchedEffect(state) { if (state !is AlertState.Active) finish() }
                (state as? AlertState.Active)?.let { active ->
                    AlarmScreen(
                        state = active,
                        onStop = { lifecycleScope.launch { alerts.stop("Stopped on the alarm screen") } },
                        onSnooze = { lifecycleScope.launch { alerts.snooze() } },
                    )
                }
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Deliberately *not* dismissing the keyguard: stopping an alarm shouldn't unlock the phone.
    }
}

@Composable
private fun AlarmScreen(state: AlertState.Active, onStop: () -> Unit, onSnooze: () -> Unit) {
    val palette = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    val elapsed by produceState(0L, state.startedAtMs) {
        while (true) {
            value = (System.currentTimeMillis() - state.startedAtMs).coerceAtLeast(0) / 1000
            delay(1000)
        }
    }
    val latest = state.sources.last()
    Surface(Modifier.fillMaxSize(), color = palette.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(Spacing.xl))
                MonitoringPulse(color = palette.red, active = state.phase == AlertPhase.RINGING, size = 96.dp)
                Spacer(Modifier.height(Spacing.l))
                DotMatrixText(if (state.phase == AlertPhase.RINGING) "Alarm" else "Snoozed", dotSize = 5.dp, color = palette.text)
                Spacer(Modifier.height(Spacing.l))
                Text(latest.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(
                    listOfNotNull(latest.app, latest.macroName).joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge, color = palette.textSecondary, textAlign = TextAlign.Center,
                )
                if (state.sources.size > 1) {
                    Text("+${state.sources.size - 1} more", style = MaterialTheme.typography.labelLarge, color = palette.textSecondary)
                }
                latest.preview?.let {
                    Spacer(Modifier.height(Spacing.m))
                    Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, maxLines = 6)
                }
                Spacer(Modifier.height(Spacing.m))
                Text(
                    "%d:%02d".format(elapsed / 60, elapsed % 60),
                    style = MaterialTheme.typography.labelLarge, color = palette.textSecondary,
                    modifier = Modifier.semantics { contentDescription = "Ringing for ${elapsed / 60} minutes ${elapsed % 60} seconds" },
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Button(
                    onClick = { haptics.performHapticFeedback(HapticFeedbackType.Confirm); onStop() },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.red, contentColor = palette.onRed),
                    modifier = Modifier.size(180.dp),
                ) { Text("STOP", fontSize = 32.sp, style = MaterialTheme.typography.labelLarge) }
                if (state.phase == AlertPhase.RINGING && state.snoozesLeft > 0) {
                    OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Snooze ${state.snoozeMs / 60_000} min (${state.snoozesLeft} left)")
                    }
                }
            }
        }
    }
}
