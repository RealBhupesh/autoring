package com.priorityring.engine.serialization

import com.priorityring.engine.model.ActionNode
import com.priorityring.engine.model.ConditionNode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Polymorphic JSON with a `"type"` field, like kotlinx's built-in sealed-class support, but
 * *tolerant*: an unrecognised type becomes an "Unknown" node holding the raw JSON, and is written
 * back out unchanged. That's what lets an older app version open (and re-save) a macro created by
 * a newer one without crashing or losing data.
 */
internal abstract class TypedJsonSerializer<T : Any>(
    private val baseName: String,
) : KSerializer<T> {

    /** Maps a value to its JSON type tag and concrete serializer. Return null for an Unknown node. */
    protected abstract fun tagOf(value: T): Pair<String, KSerializer<out T>>?
    protected abstract fun serializerFor(type: String): KSerializer<out T>?
    protected abstract fun unknown(type: String?, raw: JsonObject): T
    protected abstract fun rawOf(unknown: T): JsonObject

    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    @Suppress("UNCHECKED_CAST")
    override fun serialize(encoder: Encoder, value: T) {
        val json = (encoder as? JsonEncoder)
            ?: throw SerializationException("$baseName can only be written as JSON")
        val tagged = tagOf(value)
        if (tagged == null) {
            json.encodeJsonElement(rawOf(value))
            return
        }
        val (type, serializer) = tagged
        val body = json.json.encodeToJsonElement(serializer as KSerializer<T>, value).jsonObject
        // Put "type" first so the stored JSON is easy to read.
        json.encodeJsonElement(JsonObject(mapOf(TYPE to JsonPrimitive(type)) + body))
    }

    override fun deserialize(decoder: Decoder): T {
        val json = (decoder as? JsonDecoder)
            ?: throw SerializationException("$baseName can only be read from JSON")
        val obj = json.decodeJsonElement().jsonObject
        val type = obj[TYPE]?.jsonPrimitive?.contentOrNull
        val serializer = type?.let(::serializerFor) ?: return unknown(type, obj)
        return json.json.decodeFromJsonElement(serializer, JsonObject(obj - TYPE))
    }

    companion object {
        const val TYPE = "type"
    }
}

internal object ConditionNodeSerializer : TypedJsonSerializer<ConditionNode>("ConditionNode") {
    override fun tagOf(value: ConditionNode) = when (value) {
        is ConditionNode.Group -> "group" to ConditionNode.Group.serializer()
        is ConditionNode.Leaf -> "condition" to ConditionNode.Leaf.serializer()
        is ConditionNode.Unknown -> null
    }

    override fun serializerFor(type: String): KSerializer<out ConditionNode>? = when (type) {
        "group" -> ConditionNode.Group.serializer()
        "condition" -> ConditionNode.Leaf.serializer()
        else -> null
    }

    override fun unknown(type: String?, raw: JsonObject) = ConditionNode.Unknown(type, raw)
    override fun rawOf(unknown: ConditionNode) = (unknown as ConditionNode.Unknown).raw
}

internal object ActionNodeSerializer : TypedJsonSerializer<ActionNode>("ActionNode") {
    override fun tagOf(value: ActionNode) = when (value) {
        is ActionNode.Step -> "action" to ActionNode.Step.serializer()
        is ActionNode.If -> "if" to ActionNode.If.serializer()
        is ActionNode.Repeat -> "repeat" to ActionNode.Repeat.serializer()
        is ActionNode.Unknown -> null
    }

    override fun serializerFor(type: String): KSerializer<out ActionNode>? = when (type) {
        "action" -> ActionNode.Step.serializer()
        "if" -> ActionNode.If.serializer()
        "repeat" -> ActionNode.Repeat.serializer()
        else -> null
    }

    override fun unknown(type: String?, raw: JsonObject) = ActionNode.Unknown(
        uid = raw["uid"]?.jsonPrimitive?.contentOrNull ?: "unknown",
        type = type,
        raw = raw,
    )

    override fun rawOf(unknown: ActionNode) = (unknown as ActionNode.Unknown).raw
}
