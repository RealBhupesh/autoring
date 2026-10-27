package com.priorityring.engine

import com.priorityring.engine.block.Block
import com.priorityring.engine.block.BlockConfig
import com.priorityring.engine.block.EvalContext
import com.priorityring.engine.event.TriggerEvent
import com.priorityring.engine.variables.Variables
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Builds a BlockConfig from Kotlin values, e.g. config(Block, "mode" to "regex"). */
fun config(block: Block, vararg values: Pair<String, Any>): BlockConfig =
    BlockConfig.of(JsonObject(values.associate { (k, v) -> k to v.toJson() }), block.meta)

fun Any.toJson(): kotlinx.serialization.json.JsonElement = when (this) {
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Collection<*> -> JsonArray(map { it!!.toJson() })
    is kotlinx.serialization.json.JsonElement -> this
    else -> error("unsupported $this")
}

class FakeEvalContext(
    vars: Map<String, String?> = emptyMap(),
    private val time: ZonedDateTime = at(2026, 10, 27, 12, 0), // a Tuesday
) : EvalContext {
    override val event = TriggerEvent("test", 0, vars)
    override val variables = Variables(vars)
    override fun now() = time
}

fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): ZonedDateTime =
    ZonedDateTime.of(LocalDateTime.of(y, mo, d, h, mi), ZoneId.of("Asia/Kolkata"))
