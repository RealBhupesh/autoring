package com.priorityring.engine.variables

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Describes a variable for the builder's picker. [name] has no braces. */
data class VariableSpec(val name: String, val label: String, val example: String = "")

/**
 * `{name}` substitution used by every text field that accepts variables.
 *
 * Rules (kept deliberately small):
 * - `{name}` is replaced when name looks like an identifier: letters, digits, `_` and `.`,
 *   starting with a letter or `_`.
 * - Unknown or missing variables become an empty string — never an error.
 * - Braces that don't form a valid reference are left alone, so JSON bodies like
 *   `{"text":"{text}"}` work as expected.
 * - `{{` produces a literal `{` for the rare case you need `{name}` printed verbatim.
 */
object VariableTemplate {
    private val reference = Regex("""\{\{|\{([A-Za-z_][A-Za-z0-9_.]*)}""")

    fun render(template: String, lookup: (String) -> String?): String =
        reference.replace(template) { m ->
            if (m.value == "{{") "{" else lookup(m.groupValues[1]).orEmpty()
        }

    fun referencedNames(template: String): Set<String> =
        reference.findAll(template).mapNotNull { it.groups[1]?.value }.toSet()
}

/** Source of built-in variables available in every macro. */
fun interface BuiltinVariables {
    fun get(name: String): String?
}

/**
 * The variables visible during one run: the trigger's outputs first, then built-ins.
 * Values are snapshotted strings; null/absent is treated as empty by [render].
 */
class Variables(
    private val values: Map<String, String?>,
    private val builtins: BuiltinVariables = BuiltinVariables { null },
) {
    operator fun get(name: String): String? = values[name] ?: builtins.get(name)

    fun render(template: String): String = VariableTemplate.render(template, ::get)

    fun with(extra: Map<String, String?>) = Variables(values + extra, builtins)

    companion object {
        val EMPTY = Variables(emptyMap())
    }
}

/**
 * Clock-based built-ins that need no Android APIs. The Android layer composes these with device
 * ones (battery etc.).
 */
class ClockVariables(private val now: () -> ZonedDateTime) : BuiltinVariables {
    override fun get(name: String): String? {
        val t = now()
        return when (name) {
            "time" -> t.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))
            "date" -> t.toLocalDate().toString() // ISO yyyy-MM-dd, unambiguous everywhere
            "weekday" -> t.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            "timestamp" -> t.toInstant().toEpochMilli().toString()
            else -> null
        }
    }

    companion object {
        val SPECS = listOf(
            VariableSpec("time", "Current time", "21:45"),
            VariableSpec("date", "Current date", "2026-10-27"),
            VariableSpec("weekday", "Day of the week", "Tuesday"),
            VariableSpec("timestamp", "Unix time (ms)", "1793130000000"),
        )
    }
}

/** Combines several built-in sources; the first non-null answer wins. */
class CompositeBuiltins(private val sources: List<BuiltinVariables>) : BuiltinVariables {
    override fun get(name: String): String? = sources.firstNotNullOfOrNull { it.get(name) }
}
