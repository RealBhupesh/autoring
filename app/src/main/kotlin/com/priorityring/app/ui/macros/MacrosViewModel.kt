package com.priorityring.app.ui.macros

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.priorityring.data.repo.MacroRepository
import com.priorityring.data.repo.StoredMacro
import com.priorityring.engine.block.BlockRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What a macro card shows; built from the macro + block registry summaries. */
data class MacroCardUi(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val readable: Boolean,
    val triggers: String,
    val conditions: String,
    val actions: String,
    val lastRunAt: Long?,
    val warning: String?,
)

@HiltViewModel
class MacrosViewModel @Inject constructor(
    private val repo: MacroRepository,
    registry: BlockRegistry,
) : ViewModel() {
    private val summaries = MacroSummaries(registry)

    val cards = repo.macros
        .map { list -> list.map(::toCard) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null) // null = still loading

    fun setEnabled(id: String, enabled: Boolean) = viewModelScope.launch { repo.setEnabled(id, enabled) }
    fun duplicate(id: String) = viewModelScope.launch { repo.duplicate(id) }
    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }

    private fun toCard(s: StoredMacro): MacroCardUi = when (s) {
        is StoredMacro.Unreadable -> MacroCardUi(
            s.id, s.name, enabled = false, readable = false, "", "", "", s.lastRunAt,
            warning = "Can't read this macro: ${s.reason}",
        )
        is StoredMacro.Ok -> {
            val m = s.macro
            MacroCardUi(
                id = m.id,
                name = m.name.ifBlank { "Untitled macro" },
                enabled = m.enabled,
                readable = true,
                triggers = summaries.triggers(m),
                conditions = summaries.conditions(m),
                actions = summaries.actions(m),
                lastRunAt = s.lastRunAt,
                warning = when {
                    s.fromNewerVersion -> "Made with a newer PriorityRing; some parts may not work."
                    summaries.hasMissing(m) -> "Uses blocks this version doesn't have."
                    else -> null
                },
            )
        }
    }
}
