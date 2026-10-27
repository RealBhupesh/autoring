package com.priorityring.engine.blocks.core

import com.priorityring.engine.block.BlockCategory
import com.priorityring.engine.block.BlockConfig
import com.priorityring.engine.block.BlockKind
import com.priorityring.engine.block.BlockMeta
import com.priorityring.engine.block.ConditionBlock
import com.priorityring.engine.block.ConditionResult
import com.priorityring.engine.block.ConfigField
import com.priorityring.engine.block.EvalContext

/**
 * "Does {variable} contain / equal / match …?" — works on any variable, so the same block handles
 * sender filters, keyword filters, app-name filters, etc.
 *
 * Each non-blank line of the pattern is an alternative ("any of"), so one block can hold a list of
 * VIP senders.
 */
object TextMatchCondition : ConditionBlock {
    const val ID = "condition.text_match"

    private const val MAX_INPUT = 10_000 // bound regex work on huge notification bodies

    enum class Mode(val label: String) {
        CONTAINS("contains"), EXACT("is exactly"), STARTS_WITH("starts with"),
        ENDS_WITH("ends with"), REGEX("matches regex"),
    }

    override val meta = BlockMeta(
        id = ID,
        kind = BlockKind.CONDITION,
        name = "Text matches",
        description = "Checks a variable such as {sender} or {text} against text or a regex.",
        category = BlockCategory.TEXT,
        icon = "text",
        fields = listOf(
            ConfigField.VariableRef(
                "variable", "Check variable", defaultValue = "text",
                help = "Sender names come from the notification and can be spoofed by anyone who can " +
                    "set their display name. Don't rely on them for anything security-sensitive.",
            ),
            ConfigField.Dropdown(
                "mode", "How",
                options = Mode.entries.map { ConfigField.Option(it.name.lowercase(), it.label) },
            ),
            ConfigField.Text(
                "pattern", "Text", multiline = true, required = true,
                help = "One value per line; any line matching is enough.",
            ),
            ConfigField.Toggle("ignore_case", "Ignore upper/lower case", defaultValue = true),
        ),
    )

    private fun mode(config: BlockConfig) =
        Mode.entries.firstOrNull { it.name.equals(config.string("mode"), ignoreCase = true) } ?: Mode.CONTAINS

    private fun alternatives(config: BlockConfig) =
        config.string("pattern").lines().map { it.trim() }.filter { it.isNotEmpty() }

    override fun summarize(config: BlockConfig): String {
        val alts = alternatives(config)
        val what = when {
            alts.isEmpty() -> "…"
            alts.size == 1 -> "“${alts[0]}”"
            alts.size <= 3 -> alts.joinToString(" or ") { "“$it”" }
            else -> "any of ${alts.size} values"
        }
        return "{${config.string("variable")}} ${mode(config).label} $what"
    }

    override fun validate(config: BlockConfig): List<String> {
        val problems = config.validate().toMutableList()
        if (mode(config) == Mode.REGEX) {
            alternatives(config).forEach { p ->
                runCatching { Regex(p) }.onFailure { problems += "Invalid regex “$p”: ${it.message}" }
            }
        }
        return problems
    }

    override suspend fun evaluate(config: BlockConfig, context: EvalContext): ConditionResult {
        val name = config.string("variable")
        val value = context.variables[name]
        val alts = alternatives(config)
        if (alts.isEmpty()) return ConditionResult.fail("No text to match was set")
        if (value.isNullOrEmpty()) return ConditionResult.fail("{$name} is empty or not provided")

        val input = value.take(MAX_INPUT)
        val ignoreCase = config.boolean("ignore_case")
        val mode = mode(config)
        for (alt in alts) {
            val hit = when (mode) {
                Mode.CONTAINS -> input.contains(alt, ignoreCase)
                Mode.EXACT -> input.trim().equals(alt, ignoreCase)
                Mode.STARTS_WITH -> input.trimStart().startsWith(alt, ignoreCase)
                Mode.ENDS_WITH -> input.trimEnd().endsWith(alt, ignoreCase)
                Mode.REGEX -> {
                    val regex = runCatching {
                        if (ignoreCase) Regex(alt, RegexOption.IGNORE_CASE) else Regex(alt)
                    }.getOrElse { return ConditionResult.fail("Invalid regex “$alt”") }
                    regex.containsMatchIn(input)
                }
            }
            if (hit) return ConditionResult.pass("{$name} “${preview(input)}” ${mode.label} “$alt”")
        }
        return ConditionResult.fail("{$name} “${preview(input)}” doesn't ${mode.label.removeSuffix("s")} any of ${alts.size} value(s)")
    }

    private fun preview(s: String) = if (s.length <= 40) s else s.take(37) + "…"
}
