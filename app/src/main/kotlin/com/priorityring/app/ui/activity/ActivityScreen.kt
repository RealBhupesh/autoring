package com.priorityring.app.ui.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.priorityring.app.ui.components.ChipTone
import com.priorityring.app.ui.components.EmptyState
import com.priorityring.app.ui.components.ScreenHeader
import com.priorityring.app.ui.components.SectionLabel
import com.priorityring.app.ui.components.StatusChip
import com.priorityring.app.ui.theme.Spacing
import com.priorityring.data.db.RunEntity
import com.priorityring.data.repo.RunRepository
import com.priorityring.engine.run.RunOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

/** Outcome filters for the Activity list. */
enum class RunFilter(val label: String, val outcomes: Set<String>?) {
    ALL("All", null),
    RAN("Ran", setOf("success", "partial")),
    NOT_RUN("Skipped / not met", setOf("skipped", "conditions_not_met")),
    PROBLEMS("Problems", setOf("failed", "timed_out", "partial")),
    STOPPED("Stopped", setOf("stopped")),
}

@HiltViewModel
class ActivityViewModel @Inject constructor(runs: RunRepository) : ViewModel() {
    val filter = MutableStateFlow(RunFilter.ALL)

    /** Runs grouped by local day, newest first, after the filter. */
    val days = combine(runs.recent(500), filter) { list, f ->
        list.filter { f.outcomes == null || it.outcome in f.outcomes }
            .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

@Composable
fun ActivityScreen(onOpenRun: (String) -> Unit, vm: ActivityViewModel = hiltViewModel()) {
    val days by vm.days.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Spacing.m)) {
        item { ScreenHeader("Activity") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(RunFilter.entries) { f ->
                    FilterChip(selected = filter == f, onClick = { vm.filter.value = f }, label = { Text(f.label) })
                }
            }
        }
        if (days.isEmpty()) {
            item {
                EmptyState(
                    if (filter == RunFilter.ALL) "Nothing has happened yet" else "Nothing matches this filter",
                    "When a macro runs or an event is skipped, you'll see every trigger, condition " +
                        "and action here, step by step.",
                )
            }
        }
        days.forEach { (day, runs) ->
            item(key = day.toString()) { SectionLabel(dayLabel(day)) }
            items(runs, key = { it.id }) { RunRow(it, onClick = { onOpenRun(it.id) }) }
        }
    }
}

private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

fun outcomeLabel(storage: String): String =
    RunOutcome.entries.firstOrNull { it.storageName == storage }?.label ?: storage.replace('_', ' ')

fun outcomeTone(storage: String): ChipTone = when (storage) {
    "success" -> ChipTone.GOOD
    "failed", "timed_out" -> ChipTone.DANGER
    else -> ChipTone.NEUTRAL
}

/** One line in an activity list. Shared with Home's "Recent activity". */
@Composable
fun RunRow(run: RunEntity, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val time = Instant.ofEpochMilli(run.startedAt).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Show details", onClick = onClick) else Modifier)
            .heightIn(min = 48.dp).padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = Spacing.m))
        Column(Modifier.weight(1f).padding(end = Spacing.s)) {
            Text(run.macroName, style = MaterialTheme.typography.titleSmall)
            Text(run.headline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        StatusChip(outcomeLabel(run.outcome), outcomeTone(run.outcome))
    }
}
