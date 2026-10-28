package com.priorityring.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.priorityring.app.ui.components.BlockIcons
import com.priorityring.app.ui.components.ChipTone
import com.priorityring.app.ui.components.SectionLabel
import com.priorityring.app.ui.components.StatusChip
import com.priorityring.app.ui.theme.LocalPalette
import com.priorityring.app.ui.theme.Spacing
import com.priorityring.engine.block.Availability
import com.priorityring.engine.block.Block
import com.priorityring.engine.block.BlockConfig
import com.priorityring.engine.block.BlockKind
import com.priorityring.engine.block.Requirement
import com.priorityring.engine.model.BlockInstance
import com.priorityring.engine.variables.VariableSpec
import kotlinx.serialization.json.JsonObject

/** Short label for an availability, or null when the block is simply available. */
fun Availability.label(): Pair<String, ChipTone>? = when (this) {
    Availability.Available -> null
    is Availability.NeedsPermission -> "Needs ${missing.joinToString { it.label }}" to ChipTone.NEUTRAL
    is Availability.Unsupported -> reason to ChipTone.DANGER
}

/**
 * The block library: every block of one kind, searchable, grouped by category, with its tier and
 * whether it works on this phone. Unsupported blocks are listed (so users know why) but disabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockPickerSheet(
    kind: BlockKind,
    blocks: List<Block>,
    availability: (Block) -> Availability,
    onPick: (Block) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val title = when (kind) {
        BlockKind.TRIGGER -> "Add a trigger"
        BlockKind.CONDITION -> "Add a condition"
        BlockKind.ACTION -> "Add an action"
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Spacing.m)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                query, { query = it }, label = { Text("Search blocks") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
            )
            val filtered = blocks.filter {
                query.isBlank() || it.meta.name.contains(query, true) || it.meta.description.contains(query, true)
            }
            LazyColumn(Modifier.navigationBarsPadding()) {
                filtered.groupBy { it.meta.category }.toSortedMap(compareBy { it.order }).forEach { (category, list) ->
                    item(key = category.id) { SectionLabel(category.label) }
                    items(list, key = { it.meta.id }) { block ->
                        val avail = availability(block)
                        BlockRow(block, avail, enabled = avail !is Availability.Unsupported) { onPick(block) }
                    }
                }
                if (filtered.isEmpty()) item { Text("No blocks match “$query”.", Modifier.padding(Spacing.m)) }
                item { Spacer(Modifier.padding(Spacing.l)) }
            }
        }
    }
}

@Composable
private fun BlockRow(block: Block, availability: Availability, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
    ) {
        Row(Modifier.padding(Spacing.m).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(BlockIcons.of(block.meta.icon), contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(block.meta.name, style = MaterialTheme.typography.titleSmall)
                Text(block.meta.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(top = Spacing.xs)) {
                    StatusChip(block.meta.tier.label, ChipTone.NEUTRAL)
                    availability.label()?.let { (text, tone) -> StatusChip(text, tone) }
                    block.meta.playStoreNote?.let { StatusChip("Play-restricted", ChipTone.NEUTRAL) }
                }
            }
        }
    }
}

/**
 * Edits one block instance. Works on a local copy; nothing changes in the macro until Done.
 * [block] is null for a missing block (unknown id): it can only be removed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockConfigSheet(
    block: Block?,
    instance: BlockInstance,
    availability: Availability?,
    variables: List<VariableSpec>,
    onFix: (Requirement) -> Unit,
    onDone: (JsonObject) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(instance.uid) { mutableStateOf(instance.config) }
    val palette = LocalPalette.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = Spacing.m).verticalScroll(rememberScrollState()).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (block == null) {
                Text("Missing block", style = MaterialTheme.typography.titleLarge)
                Text(
                    "“${instance.blockId}” isn't part of this version of PriorityRing (maybe the macro was made with a newer one). " +
                        "It's kept as it is and skipped when the macro runs.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row { TextButton(onClick = onRemove) { Text("Remove", color = palette.redText) } }
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(BlockIcons.of(block.meta.icon), contentDescription = null)
                Spacer(Modifier.width(Spacing.s))
                Text(block.meta.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                StatusChip(block.meta.tier.label, ChipTone.NEUTRAL)
            }
            Text(block.meta.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            when (availability) {
                is Availability.NeedsPermission -> availability.missing.forEach { r ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
                        Row(Modifier.padding(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Needs: ${r.label}", style = MaterialTheme.typography.titleSmall)
                                Text(r.why, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { onFix(r) }) { Text("Allow") }
                        }
                    }
                }
                is Availability.Unsupported -> Text(
                    "Not supported on this phone: ${availability.reason}",
                    color = palette.redText, style = MaterialTheme.typography.bodyMedium,
                )
                else -> Unit
            }
            block.meta.playStoreNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            Spacer(Modifier.padding(Spacing.xs))
            Text(block.summarize(BlockConfig.of(draft, block.meta)), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.padding(Spacing.xs))

            ConfigForm(block.meta.fields, draft, variables) { draft = it }

            val problems = block.validate(BlockConfig.of(draft, block.meta))
            problems.forEach { Text(it, color = palette.redText, style = MaterialTheme.typography.bodySmall) }

            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRemove) { Text("Remove", color = palette.redText) }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onDone(draft) }) { Text("Done") }
            }
        }
    }
}
