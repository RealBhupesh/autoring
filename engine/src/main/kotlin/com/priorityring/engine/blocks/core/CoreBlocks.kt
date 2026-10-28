package com.priorityring.engine.blocks.core

import com.priorityring.engine.block.Block

/**
 * Blocks that need no Android APIs and therefore live in the engine (and are unit-tested here).
 * The Android catalogue in :blocks adds these to its own list.
 */
object CoreBlocks {
    val all: List<Block> = listOf(
        NotificationPostedTrigger,
        TextMatchCondition,
        TimeWindowCondition,
        DaysOfWeekCondition,
        WaitAction,
        StopMacroAction,
    )
}
