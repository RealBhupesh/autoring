package com.priorityring.engine.block

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * One field in a block's config form. The builder UI renders a form from a block's list of fields,
 * so a new block never needs custom UI code.
 *
 * Each field type also defines how its value is stored in the macro's JSON (see [default]) and
 * how [BlockConfig] reads it back.
 */
sealed class ConfigField {
    /** JSON key; stable forever once shipped. */
    abstract val key: String
    abstract val label: String
    abstract val help: String?

    /** The JSON stored when the user hasn't set this field. */
    abstract val default: JsonElement

    /** Returns a human-readable problem with [value], or null if it's fine. */
    open fun validate(value: JsonElement): String? = null

    /**
     * Free text. With [variables] = true the UI offers the variable picker and `{name}` references
     * are substituted at run time.
     */
    data class Text(
        override val key: String,
        override val label: String,
        val defaultValue: String = "",
        val placeholder: String? = null,
        val multiline: Boolean = false,
        val variables: Boolean = false,
        val required: Boolean = false,
        override val help: String? = null,
    ) : ConfigField() {
        override val default get() = JsonPrimitive(defaultValue)
        override fun validate(value: JsonElement): String? {
            val s = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: return "$label must be text"
            return if (required && s.isBlank()) "$label is required" else null
        }
    }

    data class Number(
        override val key: String,
        override val label: String,
        val defaultValue: Long,
        val min: Long = Long.MIN_VALUE,
        val max: Long = Long.MAX_VALUE,
        val unit: String? = null,
        override val help: String? = null,
    ) : ConfigField() {
        override val default get() = JsonPrimitive(defaultValue)
        override fun validate(value: JsonElement): String? {
            val n = (value as? JsonPrimitive)?.content?.toLongOrNull() ?: return "$label must be a whole number"
            return if (n < min || n > max) "$label must be between $min and $max" else null
        }
    }

    /** Stored as milliseconds. */
    data class Duration(
        override val key: String,
        override val label: String,
        val defaultMs: Long,
        val minMs: Long = 0,
        val maxMs: Long = Long.MAX_VALUE,
        override val help: String? = null,
    ) : ConfigField() {
        override val default get() = JsonPrimitive(defaultMs)
        override fun validate(value: JsonElement): String? {
            val n = (value as? JsonPrimitive)?.content?.toLongOrNull() ?: return "$label must be a duration"
            return if (n < minMs || n > maxMs) {
                "$label must be between ${formatDuration(minMs)} and ${formatDuration(maxMs)}"
            } else null
        }
    }

    /** Stored as `{"start": minuteOfDay, "end": minuteOfDay}`. End before start crosses midnight. */
    data class TimeRangeField(
        override val key: String,
        override val label: String,
        val defaultValue: TimeRange,
        override val help: String? = null,
    ) : ConfigField() {
        override val default get() = defaultValue.toJson()
        override fun validate(value: JsonElement): String? =
            if (TimeRange.fromJson(value) == null) "$label is not a valid time range" else null
    }

    /** Stored as an array of ISO day numbers (1 = Monday … 7 = Sunday). Empty means every day. */
    data class Days(
        override val key: String,
        override val label: String,
        val defaultDays: Set<Int> = (1..7).toSet(),
        override val help: String? = null,
    ) : ConfigField() {
        override val default get() = JsonArray(defaultDays.sorted().map { JsonPrimitive(it) })
    }

    /** Stored as an array of package names. */
    data class AppPicker(
        override val key: String,
        override val label: String,
        val multiple: Boolean = true,
        /** Pre-selected packages, e.g. Gmail for a Gmail preset. Empty = any app. */
        val defaultPackages: List<String> = emptyList(),
        override val help: String? = null,
    ) : ConfigField() {
        override val default get() = JsonArray(defaultPackages.map { JsonPrimitive(it) })
    }

    /** Stored as an array of display names (what notifications actually show). */
    data class ContactPicker(
        override val key: String,
        override val label: String,
        override val help: String? = null,
    ) : ConfigField() {
        override val default get() = JsonArray(emptyList())
    }

    /** Stored as a URI string; empty means the system default alarm sound. */
    data class SoundPicker(
        override val key: String,
        override val label: String,
        override val help: String? = null,
    ) : ConfigField() {
        override val default get() = JsonPrimitive("")
    }

    data class Option(val value: String, val label: String)

    /** Stored as the selected option's [Option.value]. */
    data class Dropdown(
        override val key: String,
        override val label: String,
        val options: List<Option>,
        val defaultValue: String = options.first().value,
        override val help: String? = null,
    ) : ConfigField() {
        init {
            require(options.isNotEmpty()) { "Dropdown $key needs options" }
        }

        override val default get() = JsonPrimitive(defaultValue)
        override fun validate(value: JsonElement): String? {
            val v = (value as? JsonPrimitive)?.content
            return if (options.none { it.value == v }) "$label: \"$v\" is not an option" else null
        }
    }

    data class Toggle(
        override val key: String,
        override val label: String,
        val defaultValue: Boolean = false,
        override val help: String? = null,
    ) : ConfigField() {
        override val default get() = JsonPrimitive(defaultValue)
    }

    data class Slider(
        override val key: String,
        override val label: String,
        val defaultValue: Int,
        val min: Int,
        val max: Int,
        val step: Int = 1,
        val unit: String? = null,
        override val help: String? = null,
    ) : ConfigField() {
        override val default get() = JsonPrimitive(defaultValue)
        override fun validate(value: JsonElement): String? {
            val n = (value as? JsonPrimitive)?.content?.toIntOrNull() ?: return "$label must be a number"
            return if (n < min || n > max) "$label must be between $min and $max" else null
        }
    }

    /**
     * Pick one of the variables available in this macro (e.g. which variable a text-match checks).
     * Stored as the bare variable name, without braces.
     */
    data class VariableRef(
        override val key: String,
        override val label: String,
        val defaultValue: String,
        override val help: String? = null,
    ) : ConfigField() {
        override val default get() = JsonPrimitive(defaultValue)
    }
}

/** A daily time window in minutes since midnight. `start == end` means the whole day. */
data class TimeRange(val startMinute: Int, val endMinute: Int) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY && endMinute in 0 until MINUTES_PER_DAY) {
            "Minutes must be within a day"
        }
    }

    val crossesMidnight: Boolean get() = endMinute < startMinute
    val isAllDay: Boolean get() = startMinute == endMinute

    /** End-exclusive, so 09:00–17:00 contains 16:59 but not 17:00. */
    operator fun contains(minuteOfDay: Int): Boolean = when {
        isAllDay -> true
        crossesMidnight -> minuteOfDay >= startMinute || minuteOfDay < endMinute
        else -> minuteOfDay in startMinute until endMinute
    }

    fun toJson(): JsonObject = JsonObject(
        mapOf("start" to JsonPrimitive(startMinute), "end" to JsonPrimitive(endMinute)),
    )

    override fun toString(): String = "${hhmm(startMinute)}–${hhmm(endMinute)}"

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        fun of(startHour: Int, startMin: Int, endHour: Int, endMin: Int) =
            TimeRange(startHour * 60 + startMin, endHour * 60 + endMin)

        fun fromJson(e: JsonElement?): TimeRange? {
            val o = e as? JsonObject ?: return null
            val s = (o["start"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return null
            val en = (o["end"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return null
            return runCatching { TimeRange(s, en) }.getOrNull()
        }

        fun hhmm(minute: Int) = String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)
    }
}

/** "2 min", "30 s", "1 h 5 min", "250 ms". Used by summaries. */
fun formatDuration(ms: Long): String {
    if (ms < 1000) return "$ms ms"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return buildList {
        if (h > 0) add("$h h")
        if (m > 0) add("$m min")
        if (s > 0) add("$s s")
    }.joinToString(" ")
}
