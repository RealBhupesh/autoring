package com.priorityring.app.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.priorityring.app.runtime.EventDispatcher
import com.priorityring.app.runtime.Simulation
import com.priorityring.app.ui.components.PanelCard
import com.priorityring.app.ui.editor.ChoiceButton
import com.priorityring.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SimulatorViewModel @Inject constructor(private val dispatcher: EventDispatcher) : ViewModel() {
    val result = MutableStateFlow<String?>(null)

    fun send(preset: Simulation.Preset, sender: String, text: String, group: String, pkg: String, fresh: Boolean) {
        val raw = Simulation.build(
            preset, sender, text, group,
            packageName = if (preset == Simulation.Preset.CUSTOM) pkg else preset.packageName,
            // A fixed key lets you test duplicate handling: send the same thing twice.
            key = if (fresh) "sim|" + System.nanoTime() else "sim|fixed",
        )
        viewModelScope.launch {
            val r = dispatcher.dispatch(Simulation.event(raw))
            result.value = buildString {
                if (!r.matchedAny) append("No enabled macro's trigger matched this notification.")
                r.started.forEach { appendLine("▶ Started “${it.macroName}”") }
                r.notRun.forEach { appendLine("– “${it.macroName}”: ${it.outcome.label} — ${it.reason}") }
            }.trim()
        }
    }
}

/**
 * Sends a fake notification through the *real* pipeline (same parsers, engine and actions), so
 * macros can be tested without waiting for a real email. Runs are marked "Simulated" in Activity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatorScreen(onBack: () -> Unit, onOpenActivity: () -> Unit, vm: SimulatorViewModel = hiltViewModel()) {
    var preset by rememberSaveable { mutableStateOf(Simulation.Preset.GMAIL) }
    var sender by rememberSaveable { mutableStateOf("Rahul Sharma") }
    var text by rememberSaveable { mutableStateOf("Quarterly report") }
    var group by rememberSaveable { mutableStateOf("Family") }
    var pkg by rememberSaveable { mutableStateOf("com.example.app") }
    var fresh by rememberSaveable { mutableStateOf(true) }
    val result by vm.result.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event simulator") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.m).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                "Pretend a notification arrived. It goes through the same parsing and macros as a real one, " +
                    "so alarms really ring.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChoiceButton("Notification type", Simulation.Preset.entries.map { it.name to it.label }, preset.name) {
                preset = Simulation.Preset.valueOf(it)
            }
            if (preset == Simulation.Preset.CUSTOM) {
                OutlinedTextField(pkg, { pkg = it }, label = { Text("App package") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (preset == Simulation.Preset.WHATSAPP_GROUP) {
                OutlinedTextField(group, { group = it }, label = { Text("Group name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(sender, { sender = it }, label = { Text(if (preset == Simulation.Preset.CUSTOM) "Title" else "Sender") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(text, { text = it }, label = { Text(if (preset == Simulation.Preset.GMAIL) "Subject / text" else "Message") },
                minLines = 2, modifier = Modifier.fillMaxWidth())
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(fresh, role = Role.Switch) { fresh = it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("New notification each time", style = MaterialTheme.typography.bodyLarge)
                    Text("Turn off to test duplicates: sending the same text twice should only run once.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = fresh, onCheckedChange = null)
            }
            Button(onClick = { vm.send(preset, sender, text, group, pkg, fresh) }, modifier = Modifier.fillMaxWidth()) {
                Text("Send test notification")
            }
            result?.let {
                PanelCard {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onOpenActivity) { Text("Open Activity") }
                }
            }
        }
    }
}
