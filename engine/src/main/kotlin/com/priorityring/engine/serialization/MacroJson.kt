package com.priorityring.engine.serialization

import com.priorityring.engine.model.Macro
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Result of reading a stored or imported macro. Never throws. */
sealed interface MacroDecodeResult {
    data class Ok(
        val macro: Macro,
        /** True when the JSON came from a newer app version; some fields may have been ignored. */
        val fromNewerVersion: Boolean,
    ) : MacroDecodeResult

    data class Corrupt(val reason: String) : MacroDecodeResult
}

/**
 * The single place that turns macros into JSON and back.
 *
 * Stored form (one macro, e.g. a Room row):   `{"formatVersion":1,"macro":{...}}`
 * Export file (many macros, shared by users): see [ExportBundle].
 *
 * When the stored shape ever has to change, bump [FORMAT_VERSION] and add a step to [migrations]
 * that rewrites the old JSON — the database itself never needs a migration for macro content.
 */
object MacroJson {
    const val FORMAT_VERSION = 1
    const val EXPORT_FORMAT = "priorityring.macros"

    val json: Json = Json {
        ignoreUnknownKeys = true   // fields added by newer versions are skipped, not fatal
        encodeDefaults = true      // explicit, self-describing files
        explicitNulls = false
        prettyPrint = false
    }

    private val pretty: Json = Json(json) { prettyPrint = true }

    /** JSON rewrites keyed by the version they upgrade *from*. Empty while we're on v1. */
    private val migrations: Map<Int, (JsonObject) -> JsonObject> = emptyMap()

    @Serializable
    private data class StoredMacro(val formatVersion: Int, val macro: JsonElement)

    fun encode(macro: Macro): String =
        json.encodeToString(
            StoredMacro.serializer(),
            StoredMacro(FORMAT_VERSION, json.encodeToJsonElement(Macro.serializer(), macro)),
        )

    fun decode(text: String): MacroDecodeResult = try {
        val root = json.parseToJsonElement(text).jsonObject
        val version = root["formatVersion"]?.jsonPrimitive?.intOrNull ?: 1
        val macroJson = root["macro"]?.jsonObject
            ?: return MacroDecodeResult.Corrupt("No \"macro\" object")
        decodeMacroElement(macroJson, version)
    } catch (e: SerializationException) {
        MacroDecodeResult.Corrupt(e.message ?: "Invalid JSON")
    } catch (e: IllegalArgumentException) {
        MacroDecodeResult.Corrupt(e.message ?: "Invalid JSON")
    }

    private fun decodeMacroElement(element: JsonObject, version: Int): MacroDecodeResult {
        var obj = element
        for (v in version until FORMAT_VERSION) {
            migrations[v]?.let { obj = it(obj) }
        }
        val macro = json.decodeFromJsonElement(Macro.serializer(), obj)
        return MacroDecodeResult.Ok(macro, fromNewerVersion = version > FORMAT_VERSION)
    }

    // ---- Import / export -------------------------------------------------------------------

    @Serializable
    data class ExportBundle(
        val format: String = EXPORT_FORMAT,
        val formatVersion: Int = FORMAT_VERSION,
        /** ISO-8601 timestamp, informational only. */
        val exportedAt: String,
        @SerialName("app") val appVersion: String,
        val macros: List<JsonElement>,
    )

    data class ImportResult(
        val macros: List<Macro>,
        /** One human-readable line per macro that couldn't be read. */
        val errors: List<String>,
    )

    fun export(macros: List<Macro>, exportedAt: String, appVersion: String): String =
        pretty.encodeToString(
            ExportBundle.serializer(),
            ExportBundle(
                exportedAt = exportedAt,
                appVersion = appVersion,
                macros = macros.map { json.encodeToJsonElement(Macro.serializer(), it) },
            ),
        )

    /**
     * Accepts an export bundle, a single stored macro, or a bare macro object, so users can paste
     * whatever they have. Each macro is decoded independently: one bad entry doesn't sink the rest.
     */
    fun import(text: String): ImportResult {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            return ImportResult(emptyList(), listOf("Not a PriorityRing file: ${e.message}"))
        }
        val format = (root["format"] as? JsonPrimitive)?.content
        return when {
            format == EXPORT_FORMAT -> {
                val version = root["formatVersion"]?.jsonPrimitive?.int ?: 1
                val items = root["macros"] as? kotlinx.serialization.json.JsonArray
                    ?: return ImportResult(emptyList(), listOf("File has no macros"))
                val ok = mutableListOf<Macro>()
                val errors = mutableListOf<String>()
                items.forEachIndexed { i, item ->
                    when (val r = safeDecode(item, version)) {
                        is MacroDecodeResult.Ok -> ok += r.macro
                        is MacroDecodeResult.Corrupt -> errors += "Macro #${i + 1}: ${r.reason}"
                    }
                }
                ImportResult(ok, errors)
            }
            format != null -> ImportResult(emptyList(), listOf("Unsupported file format \"$format\""))
            "macro" in root -> single(decode(text))
            else -> single(safeDecode(root, FORMAT_VERSION))
        }
    }

    private fun single(r: MacroDecodeResult) = when (r) {
        is MacroDecodeResult.Ok -> ImportResult(listOf(r.macro), emptyList())
        is MacroDecodeResult.Corrupt -> ImportResult(emptyList(), listOf(r.reason))
    }

    private fun safeDecode(element: JsonElement, version: Int): MacroDecodeResult = try {
        decodeMacroElement(element.jsonObject, version)
    } catch (e: SerializationException) {
        MacroDecodeResult.Corrupt(e.message ?: "Invalid macro")
    } catch (e: IllegalArgumentException) {
        MacroDecodeResult.Corrupt(e.message ?: "Invalid macro")
    }
}
