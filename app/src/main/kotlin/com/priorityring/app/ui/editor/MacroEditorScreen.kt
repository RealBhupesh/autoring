package com.priorityring.app.ui.editor

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.priorityring.app.ui.components.BlockIcons
import com.priorityring.app.ui.components.SectionLabel
import com.priorityring.app.ui.components.StatusChip
import com.priorityring.app.ui.theme.LocalPalette
import com.priorityring.app.ui.theme.Spacing
import com.priorityring.engine.block.Availability
import com.priorityring.engine.block.Block
import com.priorityring.engine.block.BlockConfig
import com.priorityring.engine.block.BlockKind
import com.priorityring.engine.block.ConfigField
import com.priorityring.engine.block.Requirement
import com.priorityring.engine.model.ActionNode
import com.priorityring.engine.model.BlockInstance
import com.priorityring.engine.model.ConditionNode
import com.priorityring.engine.model.FailurePolicy
import com.priorityring.engine.model.LogicOp
import com.priorityring.engine.model.Macro
import com.priorityring.engine.model.MacroSettings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The macro builder: a vertical WHEN → IF → THEN layout. Every block opens a form generated from
 * its schema ([ConfigForm]); a live plain-English sentence at the top says what the macro does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroEditorScreen(onClose: () -> Unit, vm: MacroEditorViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var picking by rememberSaveable { mutableStateOf<BlockKind?>(null) }
    var editingUid by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var initial by remember { mutableStateOf<Macro?>(null) }
    // Bumped on resume, so availability is re-checked after returning from a system settings screen.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { resumeTick++; onPauseOrDispose { } }

    LaunchedEffect(state.loading) { if (!state.loading && initial == null) initial = state.macro }
    LaunchedEffect(Unit) { vm.events.collect { onClose() } }

    val dirty = initial != null && state.macro != initial
    BackHandler(enabled = dirty) { confirmDiscard = true }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }
    val fix: (Requirement) -> Unit = { r ->
        if (r == Requirement.POST_NOTIFICATIONS && Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.fixIntent(r)?.let { try { context.startActivity(it) } catch (_: ActivityNotFoundException) { } }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New macro" else "Edit macro") },
                navigationIcon = {
                    IconButton(onClick = { if (dirty) confirmDiscard = true else onClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete macro") }
                    }
                    Button(onClick = vm::save, modifier = Modifier.padding(end = Spacing.s)) { Text("Save") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val macro = state.macro
        if (macro == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        // Re-created on resume, so permission badges update after visiting system settings.
        val availabilityOf = remember(resumeTick) { { b: Block -> vm.availability(b) } }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.m).testTag("editor-list"),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            item {
                OutlinedTextField(
                    value = macro.name, onValueChange = vm::rename, label = { Text("Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    vm.summaries.sentence(macro), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = Spacing.xs),
                )
            }
            if (state.errors.isNotEmpty()) {
                item {
                    Surface(color = LocalPalette.current.red, shape = MaterialTheme.shapes.small) {
                        Column(Modifier.padding(Spacing.m).fillMaxWidth()) {
                            state.errors.forEach { Text("• $it", color = LocalPalette.current.onRed) }
                        }
                    }
                }
            }

            // ---- WHEN ----
            item { SectionLabel("When · any of these happens") }
            itemsIndexed(macro.triggers, key = { _, t -> t.uid }) { _, t ->
                BlockCard(vm, availabilityOf, t, onClick = { editingUid = t.uid })
            }
            item { AddButton("Add trigger") { picking = BlockKind.TRIGGER } }

            // ---- IF ----
            item { SectionLabel("If") }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(LogicOp.AND to "All must pass", LogicOp.OR to "Any can pass").forEachIndexed { i, (op, label) ->
                        SegmentedButton(
                            selected = macro.conditions.op == op,
                            onClick = { vm.setConditionOp(op) },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label) }
                    }
                }
            }
            if (macro.conditions.children.isEmpty()) {
                item { Text("No conditions: runs every time a trigger fires.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            itemsIndexed(macro.conditions.children, key = { i, c -> (c as? ConditionNode.Leaf)?.block?.uid ?: "c$i" }) { _, c ->
                when (c) {
                    is ConditionNode.Leaf -> BlockCard(vm, availabilityOf, c.block, onClick = { editingUid = c.block.uid }) {
                        FilterChip(selected = c.negate, onClick = { vm.toggleNegate(c.block.uid) }, label = { Text("NOT") })
                    }
                    else -> ReadOnlyCard(vm.summaries.condition(c))
                }
            }
            item { AddButton("Add condition") { picking = BlockKind.CONDITION } }

            // ---- THEN ----
            item { SectionLabel("Then · in this order") }
            itemsIndexed(macro.actions, key = { _, a -> a.uid }) { index, a ->
                when (a) {
                    is ActionNode.Step -> BlockCard(vm, availabilityOf, a.block, onClick = { editingUid = a.uid }, number = index + 1) {
                        IconButton(onClick = { vm.moveAction(a.uid, -1) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(onClick = { vm.moveAction(a.uid, +1) }, enabled = index < macro.actions.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                        }
                        FilterChip(
                            selected = a.onFailure == FailurePolicy.STOP_MACRO,
                            onClick = {
                                vm.setOnFailure(a.uid, if (a.onFailure == FailurePolicy.STOP_MACRO) FailurePolicy.CONTINUE else FailurePolicy.STOP_MACRO)
                            },
                            label = { Text(if (a.onFailure == FailurePolicy.STOP_MACRO) "Stop if fails" else "Continue if fails") },
                        )
                    }
                    else -> ReadOnlyCard("${index + 1}. " + vm.summaries.action(a))
                }
            }
            item { AddButton("Add action") { picking = BlockKind.ACTION } }

            // ---- settings ----
            item { SectionLabel("Settings") }
            item { SettingsCard(macro, onEnabled = vm::setEnabled, onSettings = vm::setSettings) }
            item { Spacer(Modifier.height(Spacing.xxl)) }
        }
    }

    picking?.let { kind ->
        BlockPickerSheet(
            kind = kind,
            blocks = vm.blocks(kind),
            availability = vm::availability,
            onPick = { block -> picking = null; editingUid = vm.add(block) },
            onDismiss = { picking = null },
        )
    }

    editingUid?.let { uid ->
        val inst = vm.findInstance(uid)
        if (inst == null) {
            editingUid = null
        } else {
            val block = vm.registry[inst.blockId]
            BlockConfigSheet(
                block = block,
                instance = inst,
                availability = block?.let(vm::availability),
                variables = vm.variables(),
                onFix = fix,
                onDone = { vm.updateConfig(uid, it); editingUid = null },
                onRemove = { vm.remove(uid); editingUid = null },
                onDismiss = { editingUid = null },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this macro?") },
            text = { Text("It will stop running and its settings will be removed. Its history stays in Activity.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete", color = LocalPalette.current.redText) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun BlockCard(
    vm: MacroEditorViewModel,
    availabilityOf: (Block) -> Availability,
    inst: BlockInstance,
    onClick: () -> Unit,
    number: Int? = null,
    extra: @Composable () -> Unit = {},
) {
    val block = vm.registry[inst.blockId]
    val palette = LocalPalette.current
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(BlockIcons.of(block?.meta?.icon ?: "missing"), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.s))
                Text(
                    (number?.let { "$it. " } ?: "") + (block?.meta?.name ?: "Missing block"),
                    style = MaterialTheme.typography.labelLarge, color = palette.textSecondary, modifier = Modifier.weight(1f),
                )
                if (block != null) availabilityOf(block).label()?.let { (t, tone) -> StatusChip(t, tone) }
            }
            Text(
                vm.registry.summarize(inst.blockId, inst.config), style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            if (block == null) Text("Not available in this version; tap to remove.", color = palette.redText, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) { extra() }
        }
    }
}

@Composable
private fun ReadOnlyCard(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            Text("Advanced part (made outside the basic builder): kept as is.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(Spacing.s))
        Text(label)
    }
}

/** Per-macro settings, rendered with the same schema-driven form as blocks. */
private val settingsFields = listOf(
    ConfigField.Duration("cooldown", "Cooldown between runs", defaultMs = 0, minMs = 0, maxMs = 24 * 3_600_000L,
        help = "Ignore new triggers for this long after a run. 0 = no cooldown."),
    ConfigField.Duration("max_run", "Max run time", defaultMs = 5 * 60_000L, minMs = 5_000, maxMs = 60 * 60_000L,
        help = "A run still going after this long is stopped and logged as timed out."),
    ConfigField.Toggle("skip_if_running", "Don't start again while running", defaultValue = true),
    ConfigField.Number("max_per_hour", "Max runs per hour", defaultValue = 60, min = 1, max = 1000,
        help = "Safety limit against runaway triggers."),
)

@Composable
private fun SettingsCard(macro: Macro, onEnabled: (Boolean) -> Unit, onSettings: (MacroSettings) -> Unit) {
    val s = macro.settings
    val values = JsonObject(
        mapOf(
            "cooldown" to JsonPrimitive(s.cooldownMs),
            "max_run" to JsonPrimitive(s.maxRunTimeMs),
            "skip_if_running" to JsonPrimitive(s.skipIfRunning),
            "max_per_hour" to JsonPrimitive(s.maxRunsPerHour),
        ),
    )
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = macro.enabled, onCheckedChange = onEnabled)
            }
            ConfigForm(settingsFields, values, emptyList()) { v ->
                val c = BlockConfig(v, settingsFields)
                onSettings(
                    MacroSettings(
                        cooldownMs = c.long("cooldown"),
                        maxRunTimeMs = c.long("max_run"),
                        skipIfRunning = c.boolean("skip_if_running"),
                        maxRunsPerHour = c.int("max_per_hour"),
                    ),
                )
            }
        }
    }
}
