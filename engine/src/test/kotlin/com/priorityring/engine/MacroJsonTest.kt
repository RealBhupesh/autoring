package com.priorityring.engine

import com.priorityring.engine.block.BlockRegistry
import com.priorityring.engine.blocks.core.CoreBlocks
import com.priorityring.engine.blocks.core.TextMatchCondition
import com.priorityring.engine.model.ActionNode
import com.priorityring.engine.model.BlockInstance
import com.priorityring.engine.model.ConditionNode
import com.priorityring.engine.model.FailurePolicy
import com.priorityring.engine.model.LogicOp
import com.priorityring.engine.model.Macro
import com.priorityring.engine.model.MacroSettings
import com.priorityring.engine.serialization.MacroDecodeResult
import com.priorityring.engine.serialization.MacroJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MacroJsonTest {

    private fun inst(uid: String, blockId: String, vararg cfg: Pair<String, String>) =
        BlockInstance(uid, blockId, JsonObject(cfg.associate { it.first to JsonPrimitive(it.second) }))

    private val sample = Macro(
        id = "m1",
        name = "VIP email",
        triggers = listOf(
            inst("t1", "trigger.notification_posted"),
            inst("t2", "trigger.manual"),
        ),
        conditions = ConditionNode.Group(
            op = LogicOp.AND,
            children = listOf(
                ConditionNode.Leaf(inst("c1", TextMatchCondition.ID, "variable" to "sender", "pattern" to "Rahul Sharma")),
                ConditionNode.Group(
                    LogicOp.OR,
                    listOf(ConditionNode.Leaf(inst("c2", "condition.days")), ConditionNode.Leaf(inst("c3", "condition.time_window"))),
                    negate = true,
                ),
            ),
        ),
        actions = listOf(
            ActionNode.Step(inst("a1", "action.ring_alarm"), onFailure = FailurePolicy.STOP_MACRO),
            ActionNode.Repeat("r1", count = 3, body = listOf(ActionNode.Step(inst("a2", "action.vibrate")))),
            ActionNode.If(
                "i1",
                condition = ConditionNode.Group(children = listOf(ConditionNode.Leaf(inst("c4", "condition.charging")))),
                then = listOf(ActionNode.Step(inst("a3", "control.wait", "duration" to "5000"))),
                otherwise = listOf(ActionNode.Step(inst("a4", "control.stop"))),
            ),
        ),
        settings = MacroSettings(cooldownMs = 60_000, maxRunTimeMs = 120_000, skipIfRunning = false, maxRunsPerHour = 5),
    )

    @Test
    fun `round trip preserves every field`() {
        val text = MacroJson.encode(sample)
        val decoded = assertIs<MacroDecodeResult.Ok>(MacroJson.decode(text))
        assertEquals(sample, decoded.macro)
        assertEquals(false, decoded.fromNewerVersion)
    }

    @Test
    fun `stored json is versioned and node types are tagged`() {
        val root = MacroJson.json.parseToJsonElement(MacroJson.encode(sample)).jsonObject
        assertEquals(1, root["formatVersion"]!!.jsonPrimitive.content.toInt())
        val actions = root["macro"]!!.jsonObject["actions"]!!.jsonArray
        assertEquals(listOf("action", "repeat", "if"), actions.map { it.jsonObject["type"]!!.jsonPrimitive.content })
    }

    @Test
    fun `unknown block ids survive decoding and re-encoding`() {
        val m = sample.copy(actions = listOf(ActionNode.Step(inst("x", "action.from_the_future", "power" to "9000"))))
        val decoded = (MacroJson.decode(MacroJson.encode(m)) as MacroDecodeResult.Ok).macro
        assertEquals(m, decoded)
        val registry = BlockRegistry(CoreBlocks.all)
        val step = decoded.actions.single() as ActionNode.Step
        assertEquals(null, registry[step.block.blockId])
        assertEquals("Missing block (action.from_the_future)", registry.summarize(step.block.blockId, step.block.config))
    }

    @Test
    fun `unknown node types from newer versions are kept verbatim`() {
        val newer = """
            {"formatVersion":2,"macro":{"id":"m9","name":"Future","newTopLevelField":true,
             "conditions":{"op":"AND","children":[{"type":"geofence_group","radius":50}]},
             "actions":[{"type":"parallel","uid":"p1","branches":[]},
                        {"type":"action","block":{"uid":"a","blockId":"control.log"}}]}}
        """.trimIndent()
        val result = assertIs<MacroDecodeResult.Ok>(MacroJson.decode(newer))
        assertTrue(result.fromNewerVersion)
        val unknownAction = assertIs<ActionNode.Unknown>(result.macro.actions[0])
        assertEquals("parallel", unknownAction.type)
        assertEquals("p1", unknownAction.uid)
        assertIs<ConditionNode.Unknown>(result.macro.conditions.children[0])

        // Re-encode: the unknown parts must come back out identically.
        val again = MacroJson.json.parseToJsonElement(MacroJson.encode(result.macro)).jsonObject["macro"]!!.jsonObject
        assertEquals(
            MacroJson.json.parseToJsonElement("""{"type":"parallel","uid":"p1","branches":[]}"""),
            again["actions"]!!.jsonArray[0],
        )
        assertEquals("geofence_group", again["conditions"]!!.jsonObject["children"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `corrupt input is reported, never thrown`() {
        assertIs<MacroDecodeResult.Corrupt>(MacroJson.decode("not json"))
        assertIs<MacroDecodeResult.Corrupt>(MacroJson.decode("""{"formatVersion":1}"""))
        assertIs<MacroDecodeResult.Corrupt>(MacroJson.decode("""{"formatVersion":1,"macro":{"name":"no id"}}"""))
        assertIs<MacroDecodeResult.Corrupt>(MacroJson.decode("[]"))
    }

    @Test
    fun `missing optional fields get defaults`() {
        val minimal = """{"formatVersion":1,"macro":{"id":"a","name":"b"}}"""
        val m = (MacroJson.decode(minimal) as MacroDecodeResult.Ok).macro
        assertEquals(true, m.enabled)
        assertEquals(MacroSettings(), m.settings)
        assertTrue(m.triggers.isEmpty() && m.actions.isEmpty() && m.conditions.children.isEmpty())
    }

    @Test
    fun `export then import round trips and isolates bad entries`() {
        val text = MacroJson.export(listOf(sample, sample.copy(id = "m2", name = "Other")), "2026-10-27T12:00:00Z", "0.1.0")
        val imported = MacroJson.import(text)
        assertEquals(listOf(sample, sample.copy(id = "m2", name = "Other")), imported.macros)
        assertTrue(imported.errors.isEmpty())

        val broken = """{"format":"priorityring.macros","formatVersion":1,"exportedAt":"x","app":"y",
            "macros":[{"name":"missing id"}, ${MacroJson.json.encodeToString(Macro.serializer(), sample)}]}"""
        val partial = MacroJson.import(broken)
        assertEquals(listOf(sample), partial.macros)
        assertEquals(1, partial.errors.size)
        assertTrue(partial.errors[0].startsWith("Macro #1"))
    }

    @Test
    fun `import accepts a single stored macro and rejects foreign files`() {
        assertEquals(listOf(sample), MacroJson.import(MacroJson.encode(sample)).macros)
        assertEquals(1, MacroJson.import("""{"format":"tasker.xml"}""").errors.size)
        assertEquals(1, MacroJson.import("garbage").errors.size)
    }
}
