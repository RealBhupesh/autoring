package com.priorityring.app.ui.activity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class ActivityViewModel @Inject constructor(runs: RunRepository) : ViewModel() {
    /** Runs grouped by local day, newest first. */
    val days = runs.recent(500)
        .map { list -> list.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

@Composable
fun ActivityScreen(vm: ActivityViewModel = hiltViewModel()) {
    val days by vm.days.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Spacing.m)) {
        item { ScreenHeader("Activity") }
        if (days.isEmpty()) {
            item {
                EmptyState(
                    "Nothing has happened yet",
                    "When a macro runs or an event is skipped, you'll see every trigger, condition " +
                        "and action here, step by step.",
                )
            }
        }
        days.forEach { (day, runs) ->
            item(key = day.toString()) { SectionLabel(dayLabel(day)) }
            items(runs, key = { it.id }) { RunRow(it) }
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

/** One line in an activity list. Shared with Home's "Recent activity". */
@Composable
fun RunRow(run: RunEntity, modifier: Modifier = Modifier) {
    val time = Instant.ofEpochMilli(run.startedAt).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(run.macroName, style = MaterialTheme.typography.titleSmall)
            Text(run.headline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val tone = when (run.outcome) {
            "success" -> ChipTone.GOOD
            "failed", "timed_out" -> ChipTone.DANGER
            else -> ChipTone.NEUTRAL
        }
        StatusChip(run.outcome.replace('_', ' '), tone)
    }
}
