package com.priorityring.engine

import com.priorityring.engine.block.TimeRange
import com.priorityring.engine.blocks.core.DaysOfWeekCondition
import com.priorityring.engine.blocks.core.TextMatchCondition
import com.priorityring.engine.blocks.core.TimeWindowCondition
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextMatchConditionTest {
    private val b = TextMatchCondition

    private suspend fun eval(value: String?, vararg cfg: Pair<String, Any>) =
        b.evaluate(config(b, *cfg), FakeEvalContext(mapOf("sender" to value)))

    @Test
    fun contains() = runTest {
        assertTrue(eval("Rahul Sharma via Gmail", "variable" to "sender", "pattern" to "rahul sharma").passed)
        assertFalse(eval("Rahul Verma", "variable" to "sender", "pattern" to "Rahul Sharma").passed)
    }

    @Test
    fun `case sensitivity is respected when ignore_case is off`() = runTest {
        assertFalse(eval("rahul", "variable" to "sender", "pattern" to "Rahul", "ignore_case" to false).passed)
    }

    @Test
    fun `any line matches`() = runTest {
        val r = eval("Priya", "variable" to "sender", "mode" to "exact", "pattern" to "Rahul Sharma\n  \nPriya\n")
        assertTrue(r.passed, r.reason)
    }

    @Test
    fun `exact, starts and ends`() = runTest {
        assertTrue(eval(" Boss ", "variable" to "sender", "mode" to "exact", "pattern" to "boss").passed)
        assertFalse(eval("Boss man", "variable" to "sender", "mode" to "exact", "pattern" to "boss").passed)
        assertTrue(eval("Boss man", "variable" to "sender", "mode" to "starts_with", "pattern" to "boss").passed)
        assertTrue(eval("Big Boss", "variable" to "sender", "mode" to "ends_with", "pattern" to "boss").passed)
    }

    @Test
    fun regex() = runTest {
        assertTrue(eval("INR 500 debited", "variable" to "sender", "mode" to "regex", "pattern" to """\bdebit(ed)?\b""").passed)
        assertFalse(eval("credited", "variable" to "sender", "mode" to "regex", "pattern" to """\bdebited\b""").passed)
    }

    @Test
    fun `invalid regex fails with a reason and is caught by validation`() = runTest {
        val r = eval("x", "variable" to "sender", "mode" to "regex", "pattern" to "(unclosed")
        assertFalse(r.passed)
        assertTrue("Invalid regex" in r.reason)
        assertTrue(b.validate(config(b, "mode" to "regex", "pattern" to "(unclosed")).any { "Invalid regex" in it })
    }

    @Test
    fun `missing or empty variable fails instead of crashing`() = runTest {
        assertEquals("{sender} is empty or not provided", eval(null, "variable" to "sender", "pattern" to "x").reason)
        assertFalse(eval("", "variable" to "sender", "pattern" to "x").passed)
        assertFalse(
            b.evaluate(config(b, "variable" to "not_set", "pattern" to "x"), FakeEvalContext()).passed,
        )
    }

    @Test
    fun `empty pattern never matches and fails validation`() = runTest {
        assertFalse(eval("anything", "variable" to "sender", "pattern" to "   ").passed)
        assertTrue(b.validate(config(b)).isNotEmpty())
    }

    @Test
    fun summaries() {
        assertEquals("{sender} contains “Rahul”", b.summarize(config(b, "variable" to "sender", "pattern" to "Rahul")))
        assertEquals("{text} matches regex “a” or “b”", b.summarize(config(b, "mode" to "regex", "pattern" to "a\nb")))
        assertEquals("{text} contains any of 4 values", b.summarize(config(b, "pattern" to "a\nb\nc\nd")))
    }
}

class TimeWindowConditionTest {
    private val b = TimeWindowCondition

    private fun range(s: Int, e: Int) = TimeRange(s * 60, e * 60).toJson()

    private suspend fun passes(time: java.time.ZonedDateTime, vararg cfg: Pair<String, Any>) =
        b.evaluate(config(b, *cfg), FakeEvalContext(time = time)).passed

    @Test
    fun `simple window is end exclusive`() = runTest {
        val r = "range" to range(9, 23)
        assertTrue(passes(at(2026, 10, 27, 9, 0), r))
        assertTrue(passes(at(2026, 10, 27, 22, 59), r))
        assertFalse(passes(at(2026, 10, 27, 23, 0), r))
        assertFalse(passes(at(2026, 10, 27, 8, 59), r))
    }

    @Test
    fun `window crossing midnight`() = runTest {
        val r = "range" to range(23, 2)
        assertTrue(passes(at(2026, 10, 27, 23, 30), r))
        assertTrue(passes(at(2026, 10, 28, 1, 59), r))
        assertFalse(passes(at(2026, 10, 28, 2, 0), r))
        assertFalse(passes(at(2026, 10, 27, 12, 0), r))
    }

    @Test
    fun `days apply to the day the window starts`() = runTest {
        // Friday-only 23:00–02:00. 2026-10-30 is a Friday.
        val cfg = arrayOf("range" to range(23, 2), "days" to listOf(5))
        assertTrue(passes(at(2026, 10, 30, 23, 15), *cfg))   // Fri night
        assertTrue(passes(at(2026, 10, 31, 1, 30), *cfg))    // Sat early = Fri's window
        assertFalse(passes(at(2026, 10, 31, 23, 15), *cfg))  // Sat night
        assertFalse(passes(at(2026, 10, 30, 1, 30), *cfg))   // Fri early = Thu's window
    }

    @Test
    fun `equal start and end means all day`() = runTest {
        assertTrue(passes(at(2026, 10, 27, 3, 0), "range" to range(0, 0)))
    }

    @Test
    fun `corrupt stored range falls back to default`() = runTest {
        assertTrue(passes(at(2026, 10, 27, 12, 0), "range" to "garbage"))
        assertFalse(passes(at(2026, 10, 27, 23, 30), "range" to "garbage"))
    }

    @Test
    fun summaries() {
        assertEquals("Between 09:00 and 23:00", b.summarize(config(b)))
        assertEquals("Between 23:00 and 02:00 on weekdays", b.summarize(config(b, "range" to range(23, 2), "days" to listOf(1, 2, 3, 4, 5))))
    }
}

class DaysOfWeekConditionTest {
    private val b = DaysOfWeekCondition

    @Test
    fun evaluate() = runTest {
        // 2026-10-27 is a Tuesday
        assertTrue(b.evaluate(config(b, "days" to listOf(2)), FakeEvalContext()).passed)
        assertFalse(b.evaluate(config(b, "days" to listOf(6, 7)), FakeEvalContext()).passed)
        // Default is weekdays
        assertTrue(b.evaluate(config(b), FakeEvalContext()).passed)
        // Empty stored list = every day
        assertTrue(b.evaluate(config(b, "days" to emptyList<Int>()), FakeEvalContext(time = at(2026, 11, 1, 9, 0))).passed)
    }

    @Test
    fun summaries() {
        assertEquals("On weekdays", b.summarize(config(b)))
        assertEquals("On weekends", b.summarize(config(b, "days" to listOf(7, 6))))
        assertEquals("On Mon–Wed", b.summarize(config(b, "days" to listOf(1, 2, 3))))
        assertEquals("On Mon, Fri", b.summarize(config(b, "days" to listOf(5, 1))))
    }
}
