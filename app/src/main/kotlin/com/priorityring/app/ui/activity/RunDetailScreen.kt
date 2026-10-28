package com.priorityring.app.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.priorityring.app.navigation.RunDetailRoute
import com.priorityring.app.ui.components.PanelCard
import com.priorityring.app.ui.components.SectionLabel
import com.priorityring.app.ui.components.StatusChip
import com.priorityring.app.ui.theme.LocalPalette
import com.priorityring.app.ui.theme.Spacing
import com.priorityring.data.db.RunEntity
import com.priorityring.data.repo.RunRepository
import com.priorityring.engine.block.formatDuration
import com.priorityring.engine.run.RunTrace
import com.priorityring.engine.run.StepStatus
import com.priorityring.engine.serialization.MacroJson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

data class RunDetail(val entity: RunEntity, val trace: RunTrace?)

@HiltViewModel
class RunDetailViewModel @Inject constructor(savedState: SavedStateHandle, runs: RunRepository) : ViewModel() {
    private val id = savedState.toRoute<RunDetailRoute>().id
    val detail = runs.observe(id)
        .map { e -> e?.let { RunDetail(it, runCatching { MacroJson.json.decodeFromString(RunTrace.serializer(), it.traceJson) }.getOrNull()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Step-by-step view of one run: trigger, each condition with its reason, each action with result and time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailScreen(onBack: () -> Unit, vm: RunDetailViewModel = hiltViewModel()) {
    val detail by vm.detail.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.entity?.macroName ?: "Run") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val d = detail ?: return@Scaffold
        val t = d.trace
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.m)) {
            item {
                PanelCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(d.entity.startedAt)),
                            style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f),
                        )
                        StatusChip(outcomeLabel(d.entity.outcome), outcomeTone(d.entity.outcome))
                    }
                    Text(d.entity.headline, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Spacing.s))
                    t?.reason?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    t?.durationMs?.let { Text("Took ${formatDuration(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (t?.simulated == true) Text("Simulated event", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (t == null) {
                item { Text("This run's details couldn't be read.", Modifier.padding(Spacing.m)) }
                return@LazyColumn
            }
            item { SectionLabel("Steps") }
            items(t.steps) { step ->
                val palette = LocalPalette.current
                val (mark, color, word) = when (step.status) {
                    StepStatus.OK -> Triple("✓", palette.text, "passed")
                    StepStatus.FAILED -> Triple("✗", palette.redText, "failed")
                    StepStatus.SKIPPED -> Triple("–", palette.textSecondary, "skipped")
                    StepStatus.INFO -> Triple("•", palette.textSecondary, "")
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = (step.depth * 16).dp, top = Spacing.xs, bottom = Spacing.xs)
                        .semantics(mergeDescendants = true) { contentDescription = "${step.label}, $word. ${step.detail.orEmpty()}" },
                ) {
                    Text(mark, color = color, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            Text(step.kind.name.lowercase(), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                            step.durationMs?.takeIf { it > 0 }?.let {
                                Text(formatDuration(it), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                            }
                        }
                        Text(step.label, style = MaterialTheme.typography.bodyMedium)
                        step.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary) }
                    }
                }
            }
            val vars = t.variables.filterValues { !it.isNullOrBlank() }
            if (vars.isNotEmpty()) {
                item { SectionLabel("Event variables") }
                items(vars.entries.toList()) { (k, v) ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text("{$k}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(140.dp))
                        Text(v.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    Text(
                        "Message text is only kept if “Store notification text” is on in Settings.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.s),
                    )
                }
            }
            item { Spacer(Modifier.padding(Spacing.xl)) }
        }
    }
}
