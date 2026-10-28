package com.priorityring.app.ui.macros

import com.priorityring.engine.block.BlockRegistry
import com.priorityring.engine.model.ActionNode
import com.priorityring.engine.model.ConditionNode
import com.priorityring.engine.model.LogicOp
import com.priorityring.engine.model.Macro

/** Plain-English descriptions of a macro, built from each block's own summary. */
class MacroSummaries(private val registry: BlockRegistry) {

    fun triggers(m: Macro): String =
        m.triggers.joinToString(" or ") { registry.summarize(it.blockId, it.config) }.ifEmpty { "No trigger" }

    fun conditions(m: Macro): String = condition(m.conditions).ifEmpty { "Always" }

    fun actions(m: Macro, max: Int = 3): String {
        val lines = m.actions.map(::action)
        return when {
            lines.isEmpty() -> "No actions"
            lines.size <= max -> lines.joinToString(" → ")
            else -> lines.take(max - 1).joinToString(" → ") + " → +${lines.size - (max - 1)} more"
        }
    }

    /** One sentence for the builder: "When …, if …, then …". */
    fun sentence(m: Macro): String = buildString {
        append("When ").append(triggers(m).replaceFirstChar { it.lowercase() })
        if (m.conditions.children.isNotEmpty()) append(", if ").append(conditions(m))
        append(", then ").append(actions(m, max = 99).replaceFirstChar { it.lowercase() }).append('.')
    }

    /** Top-level groups read without brackets; nested groups get them. */
    fun condition(node: ConditionNode): String = render(node, top = true)

    private fun render(node: ConditionNode, top: Boolean = false): String = when (node) {
        is ConditionNode.Leaf -> registry.summarize(node.block.blockId, node.block.config)
            .let { if (node.negate) "NOT ($it)" else it }
        is ConditionNode.Group -> {
            val inner = node.children.map { render(it) }.filter { it.isNotEmpty() }
            val body = inner.joinToString(if (node.op == LogicOp.AND) " and " else " or ")
            when {
                inner.isEmpty() -> ""
                node.negate -> "NOT ($body)"
                !top && inner.size > 1 -> "($body)"
                else -> body
            }
        }
        is ConditionNode.Unknown -> "Unknown condition"
    }

    fun action(a: ActionNode): String = when (a) {
        is ActionNode.Step -> registry.summarize(a.block.blockId, a.block.config)
        is ActionNode.If -> "If ${condition(a.condition).ifEmpty { "always" }} then ${a.then.size} action(s)"
        is ActionNode.Repeat -> "Repeat ${a.count}× (${a.body.size} action(s))"
        is ActionNode.Unknown -> "Unknown step"
    }

    /** Ids of every block the macro uses, including nested ones. */
    fun blockIds(m: Macro): List<String> = buildList {
        m.triggers.forEach { add(it.blockId) }
        fun walk(c: ConditionNode) {
            when (c) {
                is ConditionNode.Leaf -> add(c.block.blockId)
                is ConditionNode.Group -> c.children.forEach(::walk)
                is ConditionNode.Unknown -> add("?")
            }
        }
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
        walk(m.conditions)
        walkA(m.actions)
    }

    fun hasMissing(m: Macro): Boolean = blockIds(m).any { registry[it] == null }
}
