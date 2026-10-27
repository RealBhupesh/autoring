package com.priorityring.engine.block

/**
 * Lookup table of every block this build knows. Built once at startup from the catalogue.
 *
 * Lookups by id return null for unknown ids; callers show those as "missing block" rather than
 * crashing (a macro may reference a block from a newer app version).
 */
class BlockRegistry(blocks: Iterable<Block>) {
    private val byId: Map<String, Block>

    init {
        val map = LinkedHashMap<String, Block>()
        for (b in blocks) {
            require(map.put(b.meta.id, b) == null) { "Duplicate block id: ${b.meta.id}" }
            // Catch mismatches between declared kind and implemented interface early.
            val kindOk = when (b) {
                is TriggerBlock -> b.meta.kind == BlockKind.TRIGGER
                is ConditionBlock -> b.meta.kind == BlockKind.CONDITION
                is ActionBlock -> b.meta.kind == BlockKind.ACTION
            }
            require(kindOk) { "Block ${b.meta.id} declares kind ${b.meta.kind} but implements another" }
        }
        byId = map
    }

    val all: Collection<Block> get() = byId.values

    operator fun get(id: String): Block? = byId[id]
    fun trigger(id: String): TriggerBlock? = byId[id] as? TriggerBlock
    fun condition(id: String): ConditionBlock? = byId[id] as? ConditionBlock
    fun action(id: String): ActionBlock? = byId[id] as? ActionBlock

    fun ofKind(kind: BlockKind): List<Block> = byId.values.filter { it.meta.kind == kind }

    /** Summary for a stored instance, falling back gracefully for unknown blocks. */
    fun summarize(blockId: String, config: kotlinx.serialization.json.JsonObject): String {
        val block = byId[blockId] ?: return "Missing block ($blockId)"
        return runCatching { block.summarize(BlockConfig.of(config, block.meta)) }
            .getOrElse { "${block.meta.name} (unreadable settings)" }
    }
}
