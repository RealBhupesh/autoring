package com.priorityring.app.ui.macros

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.priorityring.app.ui.components.EmptyState
import com.priorityring.app.ui.components.PanelCard
import com.priorityring.app.ui.components.ScreenHeader
import com.priorityring.app.ui.theme.LocalPalette
import com.priorityring.app.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date

@Composable
fun MacrosScreen(vm: MacrosViewModel = hiltViewModel()) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            item { ScreenHeader("Macros") }
            val list = cards
            if (list != null && list.isEmpty()) {
                item {
                    EmptyState(
                        "No macros yet",
                        "The macro builder arrives in the next milestone. For now you can add a sample " +
                            "macro to check that saving works.",
                    )
                }
            }
            items(list.orEmpty(), key = { it.id }) { card ->
                MacroCard(
                    card,
                    onToggle = { vm.setEnabled(card.id, it) },
                    onDuplicate = { vm.duplicate(card.id) },
                    onDelete = { confirmDelete = card.id },
                )
            }
            item { Spacer(Modifier.height(96.dp)) } // room for the FAB
        }

        ExtendedFloatingActionButton(
            onClick = vm::addSample,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Add sample") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.m),
        )
    }

    confirmDelete?.let { id ->
        val name = cards?.firstOrNull { it.id == id }?.name ?: "this macro"
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete “$name”?") },
            text = { Text("This can't be undone yet. (Undo arrives with the builder.)") },
            confirmButton = {
                TextButton(onClick = { vm.delete(id); confirmDelete = null }) {
                    Text("Delete", color = LocalPalette.current.redText)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MacroCard(
    card: MacroCardUi,
    onToggle: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(card.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = card.enabled,
                enabled = card.readable,
                onCheckedChange = {
                    haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    onToggle(it)
                },
                modifier = Modifier.semantics { contentDescription = "Enable ${card.name}" },
            )
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options for ${card.name}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (card.readable) {
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                    }
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                }
            }
        }
        if (card.readable) {
            Line("WHEN", card.triggers)
            Line("IF", card.conditions)
            Line("THEN", card.actions)
        }
        card.warning?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = palette.redText, modifier = Modifier.padding(top = Spacing.s))
        }
        Text(
            card.lastRunAt?.let { "Last run ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}" }
                ?: "Never run",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}

@Composable
private fun Line(label: String, text: String) {
    Row(Modifier.padding(top = Spacing.xs)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = LocalPalette.current.textSecondary,
            modifier = Modifier.width(48.dp), // aligns WHEN / IF / THEN columns
        )
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
