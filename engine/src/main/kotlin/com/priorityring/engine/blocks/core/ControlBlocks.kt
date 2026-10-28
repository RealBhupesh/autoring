package com.priorityring.engine.blocks.core

import com.priorityring.engine.block.ActionBlock
import com.priorityring.engine.block.ActionContext
import com.priorityring.engine.block.ActionResult
import com.priorityring.engine.block.BlockCategory
import com.priorityring.engine.block.BlockConfig
import com.priorityring.engine.block.BlockKind
import com.priorityring.engine.block.BlockMeta
import com.priorityring.engine.block.ConfigField
import com.priorityring.engine.block.formatDuration
import kotlinx.coroutines.delay

/** Pauses the macro. The macro's max run time still applies, so a long wait can time out. */
object WaitAction : ActionBlock {
    const val ID = "control.wait"

    override val meta = BlockMeta(
        id = ID,
        kind = BlockKind.ACTION,
        name = "Wait",
        description = "Pause before the next action.",
        category = BlockCategory.CONTROL,
        icon = "timer",
        fields = listOf(
            ConfigField.Duration(
                "duration", "Wait for", defaultMs = 10_000, minMs = 1_000, maxMs = 60 * 60_000,
                help = "Counts towards the macro's max run time.",
            ),
        ),
    )

    override fun summarize(config: BlockConfig) = "Wait ${formatDuration(config.long("duration"))}"

    override suspend fun execute(config: BlockConfig, context: ActionContext): ActionResult {
        val ms = config.long("duration").coerceIn(0, 60 * 60_000)
        delay(ms) // cancellable: Stop / timeout interrupt it immediately
        return ActionResult.Success("Waited ${formatDuration(ms)}")
    }
}

/** Ends the macro run here, successfully. Most useful inside an If. */
object StopMacroAction : ActionBlock {
    const val ID = "control.stop"

    override val meta = BlockMeta(
        id = ID,
        kind = BlockKind.ACTION,
        name = "Stop macro",
        description = "End this run here. Later actions are skipped.",
        category = BlockCategory.CONTROL,
        icon = "stop",
    )

    override fun summarize(config: BlockConfig) = "Stop macro"

    override suspend fun execute(config: BlockConfig, context: ActionContext): ActionResult =
        ActionResult.Stop("Stopped by a “Stop macro” action")
}
