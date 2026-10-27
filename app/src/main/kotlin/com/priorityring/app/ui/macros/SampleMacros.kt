package com.priorityring.app.ui.macros

import com.priorityring.data.repo.MacroRepository
import com.priorityring.engine.block.BlockConfig
import com.priorityring.engine.block.TimeRange
import com.priorityring.engine.blocks.core.TextMatchCondition
import com.priorityring.engine.blocks.core.TimeWindowCondition
import com.priorityring.engine.model.ActionNode
import com.priorityring.engine.model.BlockInstance
import com.priorityring.engine.model.ConditionNode
import com.priorityring.engine.model.Macro
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * M1 only: a ready-made macro so storage can be tested on the phone before the builder exists.
 * Its trigger and actions use blocks that arrive in M2, so until then they show as
 * "Missing block" — which also demonstrates that unknown blocks never crash the app.
 */
object SampleMacros {
    fun vipEmail(): Macro {
        fun id() = MacroRepository.newId()
        val textMatch = BlockConfig.defaults(TextMatchCondition.meta) + mapOf(
            "variable" to JsonPrimitive("sender"),
            "pattern" to JsonPrimitive("Rahul Sharma"),
        )
        val window = BlockConfig.defaults(TimeWindowCondition.meta) +
            mapOf("range" to TimeRange.of(9, 0, 23, 0).toJson())
        return Macro(
            id = id(),
            name = "VIP email alarm",
            triggers = listOf(
                BlockInstance(id(), "trigger.notification_posted", JsonObject(mapOf("apps" to JsonPrimitive("com.google.android.gm")))),
            ),
            conditions = ConditionNode.Group(
                children = listOf(
                    ConditionNode.Leaf(BlockInstance(id(), TextMatchCondition.ID, JsonObject(textMatch))),
                    ConditionNode.Leaf(BlockInstance(id(), TimeWindowCondition.ID, JsonObject(window))),
                ),
            ),
            actions = listOf(
                ActionNode.Step(BlockInstance(id(), "action.ring_alarm")),
                ActionNode.Step(BlockInstance(id(), "action.flash_torch")),
            ),
        )
    }
}
