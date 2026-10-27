package com.priorityring.app.ui.macros

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.priorityring.data.repo.MacroRepository
import com.priorityring.data.repo.StoredMacro
import com.priorityring.engine.block.BlockRegistry
import com.priorityring.engine.model.ActionNode
import com.priorityring.engine.model.ConditionNode
import com.priorityring.engine.model.LogicOp
import com.priorityring.engine.model.Macro
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
    private val registry: BlockRegistry,
) : ViewModel() {

    val cards = repo.macros
        .map { list -> list.map(::toCard) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null) // null = still loading

    fun setEnabled(id: String, enabled: Boolean) = viewModelScope.launch { repo.setEnabled(id, enabled) }
    fun duplicate(id: String) = viewModelScope.launch { repo.duplicate(id) }
    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }
    fun addSample() = viewModelScope.launch { repo.save(SampleMacros.vipEmail()) }

    private fun toCard(s: StoredMacro): MacroCardUi = when (s) {
        is StoredMacro.Unreadable -> MacroCardUi(
            s.id, s.name, enabled = false, readable = false, "", "", "", s.lastRunAt,
            warning = "Can't read this macro: ${s.reason}",
        )
        is StoredMacro.Ok -> {
            val m = s.macro
            MacroCardUi(
                id = m.id,
                name = m.name,
                enabled = m.enabled,
                readable = true,
                triggers = m.triggers.joinToString(" or ") { registry.summarize(it.blockId, it.config) }
                    .ifEmpty { "No trigger" },
                conditions = summarize(m.conditions).ifEmpty { "Always" },
                actions = summarizeActions(m),
                lastRunAt = s.lastRunAt,
                warning = when {
                    s.fromNewerVersion -> "Made with a newer PriorityRing; some parts may not work."
                    hasMissing(m) -> "Uses blocks this version doesn't have."
                    else -> null
                },
            )
        }
    }

    private fun summarize(node: ConditionNode): String = when (node) {
        is ConditionNode.Leaf -> registry.summarize(node.block.blockId, node.block.config)
            .let { if (node.negate) "NOT ($it)" else it }
        is ConditionNode.Group -> {
            val joiner = if (node.op == LogicOp.AND) " and " else " or "
            val inner = node.children.map(::summarize).filter { it.isNotEmpty() }
            val text = if (inner.size > 1 && node.negate) "(${inner.joinToString(joiner)})" else inner.joinToString(joiner)
            if (node.negate && text.isNotEmpty()) "NOT $text" else text
        }
        is ConditionNode.Unknown -> "Unknown condition"
    }

    private fun summarizeActions(m: Macro): String {
        val lines = m.actions.map { a ->
            when (a) {
                is ActionNode.Step -> registry.summarize(a.block.blockId, a.block.config)
                is ActionNode.If -> "If …"
                is ActionNode.Repeat -> "Repeat ${a.count}×"
                is ActionNode.Unknown -> "Unknown step"
            }
        }
        return when {
            lines.isEmpty() -> "No actions"
            lines.size <= 3 -> lines.joinToString(" → ")
            else -> lines.take(2).joinToString(" → ") + " → +${lines.size - 2} more"
        }
    }

    private fun hasMissing(m: Macro): Boolean {
        val ids = buildList {
            m.triggers.forEach { add(it.blockId) }
            fun walk(c: ConditionNode) {
                when (c) {
                    is ConditionNode.Leaf -> add(c.block.blockId)
                    is ConditionNode.Group -> c.children.forEach(::walk)
                    is ConditionNode.Unknown -> add("?")
                }
            }
            walk(m.conditions)
            fun walkA(list: List<ActionNode>) {
                list.forEach { a ->
                    when (a) {
                        is ActionNode.Step -> add(a.block.blockId)
                        is ActionNode.If -> { walk(a.condition); walkA(a.then); walkA(a.otherwise) }
                        is ActionNode.Repeat -> walkA(a.body)
                        is ActionNode.Unknown -> add("?")
                    }
                }
            }
            walkA(m.actions)
        }
        return ids.any { registry[it] == null }
    }
}
