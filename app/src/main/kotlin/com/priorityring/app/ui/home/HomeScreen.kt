package com.priorityring.app.ui.home

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.priorityring.app.ui.activity.RunRow
import com.priorityring.app.ui.components.DotMatrixText
import com.priorityring.app.ui.components.EmptyState
import com.priorityring.app.ui.components.MonitoringPulse
import com.priorityring.app.ui.components.PanelCard
import com.priorityring.app.ui.components.SectionLabel
import com.priorityring.app.ui.theme.LocalPalette
import com.priorityring.app.ui.theme.Spacing
import com.priorityring.engine.alert.AlertPhase
import com.priorityring.engine.alert.AlertState
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    onOpenMacros: () -> Unit,
    onOpenRun: (String) -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    // The test alarm's STOP button lives in a notification, so ask for permission first if needed.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.testAlarm() }
    val startTest: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.testAlarm()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.m),
    ) {
        Spacer(Modifier.height(Spacing.l))
        DotMatrixText("PriorityRing", dotSize = 4.dp)

        (state.alert as? AlertState.Active)?.let { alert ->
            Spacer(Modifier.height(Spacing.m))
            AlarmBanner(alert, onStop = vm::stopAlarm, onSnooze = vm::snoozeAlarm)
        }

        SectionLabel("Monitoring")
        HealthCard(state.health, state.lastEventAt, onRebind = vm::rebind)

        Spacer(Modifier.height(Spacing.s))
        MasterSwitch(state.masterEnabled, vm::setMasterEnabled)

        if (state.running.isNotEmpty()) {
            SectionLabel("Running now")
            PanelCard {
                state.running.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.macroName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.stopRun(r.runId) }) { Text("Stop") }
                    }
                }
                OutlinedButton(onClick = vm::stopAll, modifier = Modifier.fillMaxWidth()) { Text("Stop all running macros") }
            }
        }

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
        Spacer(Modifier.height(Spacing.s))
        OutlinedButton(onClick = startTest, modifier = Modifier.fillMaxWidth()) { Text("Test alarm (30 s)") }

        SectionLabel("Recent activity")
        if (state.recentRuns.isEmpty()) {
            PanelCard {
                EmptyState("No runs yet", "Every run and every skipped event will appear here with its reason.")
            }
        } else {
            PanelCard { state.recentRuns.forEach { RunRow(it, onClick = { onOpenRun(it.id) }) } }
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun AlarmBanner(alert: AlertState.Active, onStop: () -> Unit, onSnooze: () -> Unit) {
    val palette = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    Surface(color = palette.red, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m).semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Assertive }) {
            Text(
                if (alert.phase == AlertPhase.RINGING) "ALARM RINGING" else "ALARM SNOOZED",
                style = MaterialTheme.typography.labelLarge, color = palette.onRed,
            )
            Text(alert.sources.last().title, style = MaterialTheme.typography.titleLarge, color = palette.onRed)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(top = Spacing.s)) {
                Button(
                    onClick = { haptics.performHapticFeedback(HapticFeedbackType.Confirm); onStop() },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.onRed, contentColor = palette.red),
                ) { Text("STOP") }
                if (alert.phase == AlertPhase.RINGING && alert.snoozesLeft > 0) {
                    OutlinedButton(onClick = onSnooze) { Text("Snooze", color = palette.onRed) }
                }
            }
        }
    }
}

@Composable
private fun HealthCard(health: Health, lastEventAt: Long?, onRebind: () -> Unit) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val (color, title, body) = when (health) {
        Health.Healthy -> Triple(palette.text, "Healthy", "Listening for notifications.")
        Health.Connecting -> Triple(palette.textSecondary, "Connecting", "Waiting for Android to connect the notification listener…")
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
                lastEventAt?.let {
                    Text(
                        "Last notification: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)),
                        style = MaterialTheme.typography.labelSmall, color = palette.textSecondary,
                    )
                }
            }
        }
        if (health is Health.Broken && health.fix != null) {
            Spacer(Modifier.height(Spacing.m))
            val (label, action) = when (val fix = health.fix) {
                is HealthFix.Open -> fix.label to { try { context.startActivity(fix.intent) } catch (_: ActivityNotFoundException) { } }
                HealthFix.Rebind -> "Reconnect" to onRebind
            }
            Button(
                onClick = action,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.red, contentColor = palette.onRed),
            ) { Text(label) }
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
