package com.priorityring.app.ui.settings

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.priorityring.app.BuildConfig
import com.priorityring.app.ui.components.ChipTone
import com.priorityring.app.ui.components.PanelCard
import com.priorityring.app.ui.components.ScreenHeader
import com.priorityring.app.ui.components.SectionLabel
import com.priorityring.app.ui.components.StatusChip
import com.priorityring.app.ui.theme.LocalPalette
import com.priorityring.app.ui.theme.Spacing
import com.priorityring.data.settings.AppSettings
import com.priorityring.data.settings.ThemeMode
import com.priorityring.engine.block.Requirement

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val settings by vm.appSettings.collectAsStateWithLifecycle()
    val requirements by vm.requirements.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    // POST_NOTIFICATIONS is a normal runtime permission: it gets a system dialog, not a settings page.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refresh()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.m),
    ) {
        ScreenHeader("Settings")

        SectionLabel("Permissions & access")
        PanelCard {
            requirements.forEachIndexed { i, status ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = Spacing.s))
                RequirementRow(status) {
                    val r = status.requirement
                    if (r == Requirement.POST_NOTIFICATIONS && Build.VERSION.SDK_INT >= 33) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        status.fix?.let {
                            try { context.startActivity(it) } catch (_: ActivityNotFoundException) { }
                        }
                    }
                }
            }
            Text(
                "PriorityRing only asks for these when a macro needs them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.m),
            )
        }

        SectionLabel("Privacy")
        PanelCard {
            Text("Keep activity history for", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Spacing.s))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AppSettings.RETENTION_CHOICES.forEachIndexed { i, days ->
                    SegmentedButton(
                        selected = settings.retentionDays == days,
                        onClick = { vm.setRetention(days) },
                        shape = SegmentedButtonDefaults.itemShape(i, AppSettings.RETENTION_CHOICES.size),
                    ) { Text(if (days == 1) "1 day" else "$days days") }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = Spacing.m))
            ToggleRow(
                title = "Store notification text",
                body = "Off: history keeps the time, app, sender and outcome only. On: also a short " +
                    "excerpt of the text, to help debug macros.",
                checked = settings.storeNotificationText,
                onChange = vm::setStoreText,
            )
            HorizontalDivider(Modifier.padding(vertical = Spacing.m))
            Text(
                "Everything stays on this phone. PriorityRing has no analytics and no account. The " +
                    "only time it uses the internet is when one of your macros has an HTTP / webhook action.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedButton(onClick = { confirm = "logs" }) { Text("Delete logs") }
                OutlinedButton(onClick = { confirm = "all" }) {
                    Text("Delete everything", color = LocalPalette.current.redText)
                }
            }
        }

        SectionLabel("Appearance")
        PanelCard {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val modes = listOf(ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light", ThemeMode.SYSTEM to "System")
                modes.forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { vm.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    ) { Text(label) }
                }
            }
        }

        SectionLabel("About")
        Text(
            "PriorityRing ${BuildConfig.VERSION_NAME} · Android API ${Build.VERSION.SDK_INT}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xl))
    }

    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (what == "all") "Delete everything?" else "Delete activity history?") },
            text = {
                Text(
                    if (what == "all") "All macros and all history will be permanently deleted."
                    else "All run history will be permanently deleted. Macros are kept.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (what == "all") vm.deleteEverything() else vm.deleteLogs()
                    confirm = null
                }) { Text("Delete", color = LocalPalette.current.redText) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RequirementRow(status: RequirementStatus, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(status.requirement.label, style = MaterialTheme.typography.titleSmall)
                StatusChip(if (status.granted) "On" else "Off", if (status.granted) ChipTone.GOOD else ChipTone.NEUTRAL)
            }
            Text(status.requirement.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!status.granted) {
            TextButton(onClick = onFix) { Text("Open") }
        }
    }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.m)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}


