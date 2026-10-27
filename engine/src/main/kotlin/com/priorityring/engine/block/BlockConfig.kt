package com.priorityring.engine.block

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Typed, forgiving read access to a block instance's stored JSON config.
 *
 * Missing or wrongly-typed values fall back to the field's schema default, so a macro saved by an
 * older version (before a field existed) still runs. Reading a key that isn't in the schema is a
 * programming error in the block and throws immediately.
 */
class BlockConfig(
    val raw: JsonObject,
    private val fields: List<ConfigField>,
) {
    private val byKey = fields.associateBy { it.key }

    private fun field(key: String): ConfigField =
        byKey[key] ?: error("Block reads undeclared config key \"$key\"")

    /** Stored value if present, otherwise the schema default. */
    fun element(key: String): JsonElement = raw[key] ?: field(key).default

    private fun primitive(key: String): JsonPrimitive? = element(key) as? JsonPrimitive
    private fun defaultPrimitive(key: String) = field(key).default as JsonPrimitive

    fun string(key: String): String =
        primitive(key)?.contentOrNull ?: defaultPrimitive(key).content

    fun long(key: String): Long =
        primitive(key)?.content?.toLongOrNull() ?: defaultPrimitive(key).content.toLong()

    fun int(key: String): Int = long(key).toInt()

    fun boolean(key: String): Boolean =
        primitive(key)?.booleanOrNull ?: defaultPrimitive(key).content.toBoolean()

    fun stringList(key: String): List<String> =
        (element(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: emptyList()

    /** ISO day numbers 1..7; an empty stored list means "every day". */
    fun days(key: String): Set<Int> {
        val list = (element(key) as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }
            ?.filter { it in 1..7 }
            ?.toSet()
            ?: emptySet()
        return list.ifEmpty { (1..7).toSet() }
    }

    fun timeRange(key: String): TimeRange =
        TimeRange.fromJson(element(key))
            ?: TimeRange.fromJson(field(key).default)
            ?: TimeRange(0, 0)

    /** Runs each field's validator on the effective value. */
    fun validate(): List<String> = fields.mapNotNull { f -> f.validate(raw[f.key] ?: f.default) }

    companion object {
        fun of(raw: JsonObject, meta: BlockMeta) = BlockConfig(raw, meta.fields)

        /** A config object containing every field's default, for new block instances. */
        fun defaults(meta: BlockMeta): JsonObject =
            JsonObject(meta.fields.associate { it.key to it.default })
    }
}
