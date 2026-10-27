package com.priorityring.app.ui.home

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.priorityring.app.ui.components.DotMatrixText
import com.priorityring.app.ui.components.EmptyState
import com.priorityring.app.ui.components.MonitoringPulse
import com.priorityring.app.ui.components.PanelCard
import com.priorityring.app.ui.components.SectionLabel
import com.priorityring.app.ui.activity.RunRow
import com.priorityring.app.ui.theme.LocalPalette
import com.priorityring.app.ui.theme.Spacing

@Composable
fun HomeScreen(
    onOpenMacros: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.m),
    ) {
        Spacer(Modifier.height(Spacing.l))
        DotMatrixText("PriorityRing", dotSize = 4.dp)

        SectionLabel("Monitoring")
        HealthCard(state.health)

        Spacer(Modifier.height(Spacing.s))
        MasterSwitch(state.masterEnabled, vm::setMasterEnabled)

        SectionLabel("Macros")
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${state.enabledMacros}", style = MaterialTheme.typography.displaySmall)
                    Text(
                        "active of ${state.totalMacros} macros",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onOpenMacros) { Text("View all") }
            }
        }

        SectionLabel("Recent activity")
        if (state.recentRuns.isEmpty()) {
            PanelCard {
                EmptyState("No runs yet", "Every run and every skipped event will appear here with its reason.")
            }
        } else {
            PanelCard { state.recentRuns.forEach { RunRow(it) } }
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun HealthCard(health: Health) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val (color, title, body) = when (health) {
        Health.Healthy -> Triple(palette.text, "Healthy", "Ready to react to notifications.")
        Health.Paused -> Triple(palette.textSecondary, "Paused", "The master switch is off. Events are logged as skipped.")
        is Health.Broken -> Triple(palette.red, "Needs attention", health.reason)
    }
    PanelCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // Announce changes (e.g. access revoked) to TalkBack users.
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            MonitoringPulse(color = color, active = health == Health.Healthy)
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (health is Health.Broken) palette.redText else palette.text,
                )
                Text(body, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            }
        }
        if (health is Health.Broken && health.fix != null) {
            Spacer(Modifier.height(Spacing.m))
            Button(
                onClick = {
                    try { context.startActivity(health.fix) } catch (_: ActivityNotFoundException) { }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.red, contentColor = palette.onRed),
            ) { Text(health.fixLabel ?: "Fix") }
        }
    }
}

@Composable
private fun MasterSwitch(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    PanelCard {
        // The whole row is one toggle target (≥ 48 dp) with the switch as decoration, which is
        // what TalkBack users expect: "Master switch, on, switch".
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = enabled, role = Role.Switch) {
                    haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    onChange(it)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Master switch", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (enabled) "All enabled macros can run" else "Everything is paused",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}
