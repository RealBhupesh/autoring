package com.priorityring.engine.blocks.core

import com.priorityring.engine.block.BlockCategory
import com.priorityring.engine.block.BlockConfig
import com.priorityring.engine.block.BlockKind
import com.priorityring.engine.block.BlockMeta
import com.priorityring.engine.block.ConditionBlock
import com.priorityring.engine.block.ConditionResult
import com.priorityring.engine.block.ConfigField
import com.priorityring.engine.block.EvalContext
import com.priorityring.engine.block.TimeRange
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Passes when the current time is inside a daily window, optionally only on some days.
 *
 * For windows that cross midnight (e.g. 23:00–02:00), the days refer to the day the window
 * *starts*: "Fri 23:00–02:00" includes Saturday 01:30.
 */
object TimeWindowCondition : ConditionBlock {
    const val ID = "condition.time_window"

    override val meta = BlockMeta(
        id = ID,
        kind = BlockKind.CONDITION,
        name = "Time window",
        description = "Only between two times of day, e.g. 09:00–23:00. Can cross midnight.",
        category = BlockCategory.TIME,
        icon = "schedule",
        fields = listOf(
            ConfigField.TimeRangeField("range", "Between", TimeRange.of(9, 0, 23, 0)),
            ConfigField.Days("days", "On days", help = "For windows crossing midnight, the day the window starts."),
        ),
    )

    override fun summarize(config: BlockConfig): String {
        val range = config.timeRange("range")
        val days = config.days("days")
        val time = if (range.isAllDay) "All day" else "Between ${TimeRange.hhmm(range.startMinute)} and ${TimeRange.hhmm(range.endMinute)}"
        return if (days.size == 7) time else "$time on ${formatDays(days)}"
    }

    override suspend fun evaluate(config: BlockConfig, context: EvalContext): ConditionResult {
        val range = config.timeRange("range")
        val days = config.days("days")
        val now = context.now()
        val minute = now.hour * 60 + now.minute
        val today = now.dayOfWeek.value
        val nowText = "${TimeRange.hhmm(minute)} ${shortDay(today)}"

        if (minute !in range) return ConditionResult.fail("$nowText is outside $range")

        // Which day did the window we're in start on?
        val windowStartDay =
            if (range.crossesMidnight && minute < range.endMinute) DayOfWeek.of(today).minus(1).value else today
        return if (windowStartDay in days) {
            ConditionResult.pass("$nowText is within $range")
        } else {
            ConditionResult.fail("$nowText is within $range, but that window started on ${shortDay(windowStartDay)}, which isn't selected")
        }
    }
}

object DaysOfWeekCondition : ConditionBlock {
    const val ID = "condition.days"

    override val meta = BlockMeta(
        id = ID,
        kind = BlockKind.CONDITION,
        name = "Days of week",
        description = "Only on the selected days.",
        category = BlockCategory.TIME,
        icon = "calendar",
        fields = listOf(ConfigField.Days("days", "Days", defaultDays = setOf(1, 2, 3, 4, 5))),
    )

    override fun summarize(config: BlockConfig) = "On ${formatDays(config.days("days"))}"

    override suspend fun evaluate(config: BlockConfig, context: EvalContext): ConditionResult {
        val today = context.now().dayOfWeek.value
        val days = config.days("days")
        return if (today in days) ConditionResult.pass("Today is ${shortDay(today)}")
        else ConditionResult.fail("Today is ${shortDay(today)}, not one of ${formatDays(days)}")
    }
}

internal fun shortDay(iso: Int): String =
    DayOfWeek.of(iso).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

/** "every day", "weekdays", "weekends", "Mon–Wed", "Mon, Wed, Fri". */
internal fun formatDays(days: Set<Int>): String {
    val sorted = days.sorted()
    return when {
        sorted.size == 7 -> "every day"
        sorted == listOf(1, 2, 3, 4, 5) -> "weekdays"
        sorted == listOf(6, 7) -> "weekends"
        sorted.size >= 3 && sorted.zipWithNext().all { (a, b) -> b == a + 1 } ->
            "${shortDay(sorted.first())}–${shortDay(sorted.last())}"
        else -> sorted.joinToString(", ") { shortDay(it) }
    }
}
