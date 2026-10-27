package com.priorityring.blocks

import com.priorityring.engine.block.Block
import com.priorityring.engine.block.BlockRegistry
import com.priorityring.engine.blocks.core.CoreBlocks

/**
 * THE list of blocks this build ships. Adding a block = write its file, add one line here.
 *
 * Android-backed blocks are classes with @Inject constructors (they need a Context etc.), so the
 * catalogue receives them from Hilt; pure blocks come from [CoreBlocks].
 */
object BlockCatalog {
    fun build(androidBlocks: List<Block>): BlockRegistry =
        BlockRegistry(CoreBlocks.all + androidBlocks)
}
