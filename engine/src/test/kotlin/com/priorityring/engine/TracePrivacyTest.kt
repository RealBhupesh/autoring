package com.priorityring.engine

import com.priorityring.engine.run.RunOutcome
import com.priorityring.engine.run.RunTrace
import com.priorityring.engine.run.StepKind
import com.priorityring.engine.run.StepStatus
import com.priorityring.engine.run.TracePrivacy
import com.priorityring.engine.run.TraceStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TracePrivacyTest {
    private val secret = "Your OTP is 482913, do not share it with anyone at all please"
    private val trace = RunTrace(
        runId = "r", macroId = "m", macroName = "M", eventType = "notification.posted",
        variables = mapOf("sender" to "Bank", "app" to "Messages", "text" to secret, "title" to "Bank"),
        startedAtMs = 0, outcome = RunOutcome.CONDITIONS_NOT_MET,
        steps = listOf(
            TraceStep(StepKind.CONDITION, 1, "{text} contains “urgent”", StepStatus.FAILED,
                "{text} “${secret.take(37)}…” doesn't contain any of 1 value(s)", atMs = 0),
            TraceStep(StepKind.CONDITION, 1, "{sender} is exactly “Bank”", StepStatus.OK, "{sender} “Bank” is exactly “Bank”", atMs = 0),
        ),
    )

    @Test
    fun `default hides content but keeps sender and app`() {
        val t = TracePrivacy.apply(trace, storeText = false)
        assertEquals(setOf("sender", "app"), t.variables.keys)
        assertFalse(t.steps.any { secret.take(20) in (it.detail ?: "") })
        assertTrue(TracePrivacy.HIDDEN in t.steps[0].detail!!)
        // Sender is not content: still readable even though "Bank" also equals the title.
        assertEquals("{sender} “Bank” is exactly “Bank”", t.steps[1].detail)
    }

    @Test
    fun `opt in keeps content truncated`() {
        val long = "x".repeat(500)
        val t = TracePrivacy.apply(trace.copy(variables = trace.variables + ("text" to long)), storeText = true)
        assertEquals(TracePrivacy.MAX_STORED_CHARS, t.variables["text"]!!.length)
        assertEquals("Bank", t.variables["sender"])
    }
}
