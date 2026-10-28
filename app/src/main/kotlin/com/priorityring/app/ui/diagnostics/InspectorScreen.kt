package com.priorityring.app.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.priorityring.app.runtime.CapturedNotification
import com.priorityring.app.runtime.RecentNotifications
import com.priorityring.app.ui.components.EmptyState
import com.priorityring.app.ui.components.PanelCard
import com.priorityring.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class InspectorViewModel @Inject constructor(val recent: RecentNotifications) : ViewModel()

/**
 * Shows the raw extras of recent notifications and how PriorityRing parsed them. Redacted by
 * default, so the dump can be shared for debugging a parser without sharing message content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectorScreen(onBack: () -> Unit, vm: InspectorViewModel = hiltViewModel()) {
    val items by vm.recent.items.collectAsStateWithLifecycle()
    var redact by rememberSaveable { mutableStateOf(true) }
    var expanded by rememberSaveable { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification inspector") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { TextButton(onClick = { vm.recent.clear() }) { Text("Clear") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            item {
                Text(
                    "The last ${items.size} notifications received since PriorityRing started. Kept in memory only.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row { FilterChip(selected = redact, onClick = { redact = !redact }, label = { Text("Redact content") }) }
            }
            if (items.isEmpty()) {
                item { EmptyState("No notifications yet", "Make sure notification access is on, then wait for (or send yourself) a notification.") }
            }
            items(items, key = { it.id }) { n -> CapturedCard(n, redact, expanded == n.id, { expanded = if (expanded == n.id) null else n.id }) { copy(context, it) } }
        }
    }
}

@Composable
private fun CapturedCard(n: CapturedNotification, redact: Boolean, open: Boolean, onToggle: () -> Unit, onCopy: (String) -> Unit) {
    val p = n.parsed
    fun show(s: String?) = if (s == null) "—" else if (redact) com.priorityring.blocks.notification.NotificationDump.redactText(s) else s
    PanelCard(onClick = onToggle) {
        Text("${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(n.atMs))} · ${n.app}", style = MaterialTheme.typography.labelMedium)
        Text("parser: ${p.parser} · sender: ${show(p.sender)}", style = MaterialTheme.typography.bodyMedium)
        Text("text: ${show(p.text)}", style = MaterialTheme.typography.bodySmall, maxLines = if (open) Int.MAX_VALUE else 2)
        if (p.isSummary || p.isGroup) Text(listOfNotNull("summary".takeIf { p.isSummary }, "group".takeIf { p.isGroup }).joinToString(" · "), style = MaterialTheme.typography.labelSmall)
        if (open) {
            p.notes.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val dump = if (redact) n.redactedDump else n.fullDump
            SelectionContainer { Text(dump, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = Spacing.s)) }
            TextButton(onClick = { onCopy(dump) }) { Text(if (redact) "Copy redacted dump" else "Copy full dump") }
        }
    }
}

private fun copy(context: Context, text: String) {
    context.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText("PriorityRing notification dump", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
