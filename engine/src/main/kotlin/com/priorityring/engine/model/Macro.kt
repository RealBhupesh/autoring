package com.priorityring.engine.model

import com.priorityring.engine.serialization.ActionNodeSerializer
import com.priorityring.engine.serialization.ConditionNodeSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A user-built automation: WHEN any trigger fires → IF conditions pass → THEN run actions in order.
 *
 * The whole macro is stored as JSON. Blocks are referenced only by their string id plus a free-form
 * JSON config, so adding new blocks never changes this schema (and never needs a DB migration).
 */
@Serializable
data class Macro(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    /** Any one of these firing starts the macro. */
    val triggers: List<BlockInstance> = emptyList(),
    /** Root condition group. An empty group always passes. */
    val conditions: ConditionNode.Group = ConditionNode.Group(),
    /** Executed top to bottom; control nodes (if / repeat) nest further lists. */
    val actions: List<ActionNode> = emptyList(),
    val settings: MacroSettings = MacroSettings(),
)

/**
 * One configured use of a block inside a macro.
 *
 * @param uid unique within the macro; used to address this instance in traces and the builder UI.
 * @param blockId the registered block's id, e.g. "condition.text_match". May refer to a block this
 *   build doesn't know (after a downgrade, or an import from a newer version) — it is then shown as
 *   a "missing block" and preserved untouched when the macro is saved again.
 * @param config raw JSON values keyed by the block's config field keys. Read it through
 *   [com.priorityring.engine.block.BlockConfig], which applies schema defaults.
 */
@Serializable
data class BlockInstance(
    val uid: String,
    val blockId: String,
    val config: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class MacroSettings(
    /** Minimum time between two runs. Skipped runs are logged with the reason. */
    val cooldownMs: Long = 0,
    /** A run still going after this long is cancelled and logged as timed out. */
    val maxRunTimeMs: Long = 5 * 60_000L,
    /** If true, a trigger that fires while this macro is still running is skipped. */
    val skipIfRunning: Boolean = true,
    /** Safety rail against runaway triggers. */
    val maxRunsPerHour: Int = 60,
)

enum class LogicOp { AND, OR }

/** What happens when an action fails. */
enum class FailurePolicy { CONTINUE, STOP_MACRO }

/** A tree of conditions. Groups combine children with AND / OR; any node can be negated. */
@Serializable(with = ConditionNodeSerializer::class)
sealed interface ConditionNode {
    val negate: Boolean

    @Serializable
    data class Group(
        val op: LogicOp = LogicOp.AND,
        val children: List<ConditionNode> = emptyList(),
        override val negate: Boolean = false,
    ) : ConditionNode

    @Serializable
    data class Leaf(
        val block: BlockInstance,
        override val negate: Boolean = false,
    ) : ConditionNode

    /**
     * A node type this build doesn't understand (written by a newer version). Kept verbatim so
     * re-saving doesn't destroy it; it always evaluates to "not passed" so we fail safe.
     */
    data class Unknown(val type: String?, val raw: JsonObject) : ConditionNode {
        override val negate: Boolean get() = false
    }
}

/**
 * One entry in a macro's action list.
 *
 * Plain actions (including control-flavoured ones like Wait, Stop macro, Run macro, Log) are [Step]s
 * that point at a block. Only constructs that need to *nest* other actions — [If] and [Repeat] — are
 * built into the engine as their own node types.
 */
@Serializable(with = ActionNodeSerializer::class)
sealed interface ActionNode {
    val uid: String

    @Serializable
    data class Step(
        val block: BlockInstance,
        val onFailure: FailurePolicy = FailurePolicy.CONTINUE,
    ) : ActionNode {
        override val uid: String get() = block.uid
    }

    @Serializable
    data class If(
        override val uid: String,
        val condition: ConditionNode.Group = ConditionNode.Group(),
        val then: List<ActionNode> = emptyList(),
        val otherwise: List<ActionNode> = emptyList(),
    ) : ActionNode

    @Serializable
    data class Repeat(
        override val uid: String,
        /** Clamped to [MAX_REPEAT] at run time, whatever is stored. */
        val count: Int = 2,
        val body: List<ActionNode> = emptyList(),
    ) : ActionNode {
        companion object {
            const val MAX_REPEAT = 100
        }
    }

    /** See [ConditionNode.Unknown]; executing it fails the step with a clear reason. */
    data class Unknown(override val uid: String, val type: String?, val raw: JsonObject) : ActionNode
}
