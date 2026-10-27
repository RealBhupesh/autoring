package com.priorityring.engine

import com.priorityring.engine.block.ActionBlock
import com.priorityring.engine.block.ActionContext
import com.priorityring.engine.block.ActionResult
import com.priorityring.engine.block.Availability
import com.priorityring.engine.block.BlockCategory
import com.priorityring.engine.block.BlockConfig
import com.priorityring.engine.block.BlockKind
import com.priorityring.engine.block.BlockMeta
import com.priorityring.engine.block.BlockRegistry
import com.priorityring.engine.block.ConfigField
import com.priorityring.engine.block.DeviceCapabilities
import com.priorityring.engine.block.Requirement
import com.priorityring.engine.block.Tier
import com.priorityring.engine.block.availability
import com.priorityring.engine.block.formatDuration
import com.priorityring.engine.blocks.core.CoreBlocks
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BlockContractTest {

    private class FakeAction(
        id: String = "action.fake",
        requirements: List<Requirement> = emptyList(),
        minSdk: Int? = null,
        tier: Tier = Tier.STANDARD,
        kind: BlockKind = BlockKind.ACTION,
    ) : ActionBlock {
        override val meta = BlockMeta(
            id = id, kind = kind, name = "Fake", description = "", category = BlockCategory.CONTROL,
            icon = "x", tier = tier, requirements = requirements, minSdk = minSdk,
            fields = listOf(
                ConfigField.Duration("d", "Duration", defaultMs = 120_000, minMs = 1_000, maxMs = 600_000),
                ConfigField.Number("n", "Count", defaultValue = 3, min = 1, max = 10),
                ConfigField.Toggle("t", "Flag", defaultValue = true),
                ConfigField.Dropdown("s", "Stream", listOf(ConfigField.Option("alarm", "Alarm"), ConfigField.Option("media", "Media"))),
            ),
        )

        override fun summarize(config: BlockConfig) = "Ring for up to ${formatDuration(config.long("d"))}"
        override suspend fun execute(config: BlockConfig, context: ActionContext) = ActionResult.Success()
    }

    private fun caps(sdk: Int = 35, granted: Set<String> = emptySet()) = object : DeviceCapabilities {
        override val sdkInt = sdk
        override fun isGranted(requirement: Requirement) = requirement.id in granted
    }

    @Test
    fun `config falls back to defaults for missing and wrongly typed values`() {
        val b = FakeAction()
        val c = BlockConfig.of(JsonObject(mapOf("n" to JsonPrimitive("not a number"))), b.meta)
        assertEquals(120_000, c.long("d"))
        assertEquals(3, c.int("n"))
        assertEquals(true, c.boolean("t"))
        assertEquals("alarm", c.string("s"))
        assertEquals("Ring for up to 2 min", b.summarize(c))
    }

    @Test
    fun `reading an undeclared key is a programming error`() {
        val c = BlockConfig.of(JsonObject(emptyMap()), FakeAction().meta)
        assertFailsWith<IllegalStateException> { c.string("nope") }
    }

    @Test
    fun `schema validation reports out-of-range and invalid options`() {
        val b = FakeAction()
        val c = BlockConfig.of(
            JsonObject(mapOf("d" to JsonPrimitive(5), "n" to JsonPrimitive(99), "s" to JsonPrimitive("bogus"))),
            b.meta,
        )
        val problems = b.validate(c)
        assertEquals(3, problems.size, problems.toString())
        assertTrue(b.validate(BlockConfig.of(BlockConfig.defaults(b.meta), b.meta)).isEmpty())
    }

    @Test
    fun `availability combines sdk, tier and permissions`() {
        val needsDnd = FakeAction(requirements = listOf(Requirement.DND_ACCESS), minSdk = 30)
        assertIs<Availability.Unsupported>(needsDnd.availability(caps(sdk = 29)))
        assertEquals(Availability.NeedsPermission(listOf(Requirement.DND_ACCESS)), needsDnd.availability(caps()))
        assertEquals(Availability.Available, needsDnd.availability(caps(granted = setOf("dnd_access"))))
        assertIs<Availability.Unsupported>(FakeAction(tier = Tier.ROOT_ONLY).availability(caps()))
    }

    @Test
    fun `registry rejects duplicates and kind mismatches`() {
        assertFailsWith<IllegalArgumentException> { BlockRegistry(listOf(FakeAction(), FakeAction())) }
        assertFailsWith<IllegalArgumentException> { BlockRegistry(listOf(FakeAction(kind = BlockKind.TRIGGER))) }
    }

    @Test
    fun `block meta rejects duplicate field keys`() {
        assertFailsWith<IllegalArgumentException> {
            BlockMeta(
                "x", BlockKind.ACTION, "x", "", BlockCategory.CONTROL, "x",
                fields = listOf(ConfigField.Toggle("a", "A"), ConfigField.Toggle("a", "B")),
            )
        }
    }

    @Test
    fun `every core block has sane defaults and a summary`() {
        val registry = BlockRegistry(CoreBlocks.all)
        for (b in registry.all) {
            val c = BlockConfig.of(BlockConfig.defaults(b.meta), b.meta)
            assertTrue(b.summarize(c).isNotBlank(), b.meta.id)
            assertTrue(b.meta.id.matches(Regex("""[a-z]+\.[a-z_]+""")), "id style: ${b.meta.id}")
        }
    }

    @Test
    fun `duration formatting`() {
        assertEquals("250 ms", formatDuration(250))
        assertEquals("30 s", formatDuration(30_000))
        assertEquals("1 h 5 min", formatDuration(3_900_000))
    }
}
