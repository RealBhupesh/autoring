package com.priorityring.engine

import com.priorityring.engine.blocks.core.StopMacroAction
import com.priorityring.engine.blocks.core.WaitAction
import com.priorityring.engine.model.ActionNode
import com.priorityring.engine.model.ConditionNode
import com.priorityring.engine.model.FailurePolicy
import com.priorityring.engine.model.MacroSettings
import com.priorityring.engine.notification.KnownApps
import com.priorityring.engine.run.RunOutcome
import com.priorityring.engine.run.StepKind
import com.priorityring.engine.run.StepStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MacroEngineTest {

    // ---- matching, variables, ordering ----------------------------------------------------------

    @Test
    fun `matching notification runs actions in order with variables`() = runTest {
        val h = EngineHarness(this)
        val m = macro(
            conditions = all(senderIs("Rahul Sharma")),
            actions = listOf(log("1: {sender}"), log("2: {subject} via {app}"), log("3: {nope}|")),
        )
        val result = h.engine.dispatch(gmail(), listOf(m), paused = false)
        advanceUntilIdle()

        assertEquals(1, result.started.size)
        assertEquals(listOf("1: Rahul Sharma", "2: Quarterly report via Gmail", "3: |"), h.log)
        val t = h.traces.single()
        assertEquals(RunOutcome.SUCCESS, t.outcome)
        assertEquals(StepKind.TRIGGER, t.steps.first().kind)
        assertEquals(3, t.steps.count { it.kind == StepKind.ACTION && it.status == StepStatus.OK })
        assertTrue(h.engine.running.value.isEmpty())
    }

    @Test
    fun `non matching sender does not run and is recorded as not met`() = runTest {
        val h = EngineHarness(this)
        h.engine.dispatch(gmail(sender = "Someone Else"), listOf(macro(conditions = all(senderIs("Rahul Sharma")))), false)
        advanceUntilIdle()
        assertTrue(h.log.isEmpty())
        val t = h.traces.single()
        assertEquals(RunOutcome.CONDITIONS_NOT_MET, t.outcome)
        val cond = t.steps.single { it.kind == StepKind.CONDITION }
        assertEquals(StepStatus.FAILED, cond.status)
        assertTrue("Someone Else" in cond.detail!!, cond.detail)
    }

    @Test
    fun `trigger app filter excludes other apps silently`() = runTest {
        val h = EngineHarness(this)
        val r = h.engine.dispatch(whatsapp("Rahul", "hi"), listOf(macro(apps = listOf(KnownApps.GMAIL))), false)
        advanceUntilIdle()
        assertTrue(!r.matchedAny && h.traces.isEmpty() && h.log.isEmpty())
    }

    @Test
    fun `disabled macros never run`() = runTest {
        val h = EngineHarness(this)
        h.engine.dispatch(gmail(), listOf(macro(enabled = false)), false)
        advanceUntilIdle()
        assertTrue(h.traces.isEmpty())
    }

    @Test
    fun `AND, OR, NOT and nested groups`() = runTest {
        val h = EngineHarness(this)
        val ev = whatsapp("Rahul", "URGENT: server down")
        suspend fun runs(c: ConditionNode.Group): Boolean {
            h.traces.clear()
            h.engine.dispatch(ev.copy(dedupeKey = null), listOf(macro(conditions = c)), false)
            advanceUntilIdle()
            return h.traces.single().outcome == RunOutcome.SUCCESS
        }
        assertTrue(runs(all(senderIs("Rahul"), textContains("urgent"))))
        assertTrue(!runs(all(senderIs("Rahul"), textContains("lunch"))))
        assertTrue(runs(any(senderIs("Priya"), textContains("urgent"))))
        assertTrue(!runs(any(senderIs("Priya"), textContains("lunch"))))
        assertTrue(runs(all(senderIs("Priya", negate = true))))
        assertTrue(!runs(all(senderIs("Rahul"), negate = true)))
        // (sender is Priya OR text contains urgent) AND NOT (text contains test)
        assertTrue(runs(all(any(senderIs("Priya"), textContains("urgent")), all(textContains("test"), negate = true))))
        assertTrue(runs(ConditionNode.Group())) // no conditions = always
    }

    @Test
    fun `two macros matching the same event run independently`() = runTest {
        val h = EngineHarness(this)
        val slow = macro(id = "slow", actions = listOf(step("test.sleep", "ms" to 60_000), log("slow done")))
        val fast = macro(id = "fast", actions = listOf(log("fast done")))
        h.engine.dispatch(gmail(), listOf(slow, fast), false)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf("fast done"), h.log)
        advanceUntilIdle()
        assertEquals(listOf("fast done", "slept", "slow done"), h.log)
    }

    // ---- gates ----------------------------------------------------------------------------------

    @Test
    fun `same notification never triggers twice, new content does`() = runTest {
        val h = EngineHarness(this)
        val m = listOf(macro())
        h.engine.dispatch(gmail(key = "k"), m, false); advanceUntilIdle()
        h.engine.dispatch(gmail(key = "k"), m, false); advanceUntilIdle()                // identical re-post
        h.engine.dispatch(gmail(key = "k", subject = "New"), m, false); advanceUntilIdle() // same key, new content
        assertEquals(listOf(RunOutcome.SUCCESS, RunOutcome.SKIPPED, RunOutcome.SUCCESS), h.traces.map { it.outcome })
        assertTrue(h.traces[1].reason!!.startsWith("Duplicate"))
        assertEquals(2, h.log.size)
    }

    @Test
    fun `paused master switch skips with reason`() = runTest {
        val h = EngineHarness(this)
        h.engine.dispatch(gmail(), listOf(macro()), paused = true)
        advanceUntilIdle()
        assertEquals(RunOutcome.SKIPPED, h.traces.single().outcome)
        assertTrue(h.traces.single().reason!!.startsWith("Paused"))
        assertTrue(h.log.isEmpty())
    }

    @Test
    fun `cooldown skips until it has elapsed`() = runTest {
        val h = EngineHarness(this)
        val m = listOf(macro(settings = MacroSettings(cooldownMs = 60_000, maxRunsPerHour = 100)))
        h.engine.dispatch(gmail(key = "a"), m, false); advanceUntilIdle()
        advanceTimeBy(30_000)
        h.engine.dispatch(gmail(key = "b"), m, false); advanceUntilIdle()
        advanceTimeBy(31_000)
        h.engine.dispatch(gmail(key = "c"), m, false); advanceUntilIdle()
        assertEquals(listOf(RunOutcome.SUCCESS, RunOutcome.SKIPPED, RunOutcome.SUCCESS), h.traces.map { it.outcome })
        assertTrue(h.traces[1].reason!!.startsWith("Cooldown: 30 s left"), h.traces[1].reason)
    }

    @Test
    fun `cooldown survives restart via seeded last run`() = runTest {
        val h = EngineHarness(this)
        h.engine.seedLastRun("m1", h.clock.nowMillis() - 10_000)
        h.engine.dispatch(gmail(), listOf(macro(settings = MacroSettings(cooldownMs = 60_000))), false)
        advanceUntilIdle()
        assertEquals(RunOutcome.SKIPPED, h.traces.single().outcome)
    }

    @Test
    fun `conditions not met do not consume the cooldown`() = runTest {
        val h = EngineHarness(this)
        val m = listOf(macro(conditions = all(textContains("urgent")), settings = MacroSettings(cooldownMs = 60_000)))
        h.engine.dispatch(whatsapp("A", "hello", key = "1"), m, false)
        h.engine.dispatch(whatsapp("A", "urgent!", key = "2"), m, false)
        advanceUntilIdle()
        assertEquals(listOf(RunOutcome.CONDITIONS_NOT_MET, RunOutcome.SUCCESS), h.traces.map { it.outcome })
    }

    @Test
    fun `don't run again while running is respected, and can be turned off`() = runTest {
        val h = EngineHarness(this)
        val long = listOf(step("test.sleep", "ms" to 10_000))
        val exclusive = listOf(macro(actions = long))
        h.engine.dispatch(gmail(key = "1"), exclusive, false); runCurrent()
        h.engine.dispatch(gmail(key = "2"), exclusive, false); advanceUntilIdle()
        assertEquals(listOf(RunOutcome.SKIPPED, RunOutcome.SUCCESS), h.traces.map { it.outcome })
        assertTrue(h.traces[0].reason!!.startsWith("Already running"))

        h.traces.clear()
        val parallel = listOf(macro(id = "p", actions = long, settings = MacroSettings(skipIfRunning = false)))
        h.engine.dispatch(gmail(key = "3"), parallel, false); runCurrent()
        h.engine.dispatch(gmail(key = "4"), parallel, false); runCurrent()
        assertEquals(2, h.engine.running.value.size)
        advanceUntilIdle()
        assertEquals(listOf(RunOutcome.SUCCESS, RunOutcome.SUCCESS), h.traces.map { it.outcome })
    }

    @Test
    fun `rate limit per macro`() = runTest {
        val h = EngineHarness(this)
        val m = listOf(macro(settings = MacroSettings(maxRunsPerHour = 2)))
        repeat(3) { h.engine.dispatch(gmail(key = "$it"), m, false); advanceUntilIdle() }
        assertEquals(listOf(RunOutcome.SUCCESS, RunOutcome.SUCCESS, RunOutcome.SKIPPED), h.traces.map { it.outcome })
        assertTrue(h.traces[2].reason!!.startsWith("Rate limit"))
        advanceTimeBy(61 * 60_000L)
        h.engine.dispatch(gmail(key = "later"), m, false); advanceUntilIdle()
        assertEquals(RunOutcome.SUCCESS, h.traces.last().outcome)
    }

    @Test
    fun `loop prevention`() = runTest {
        val h = EngineHarness(this)
        h.engine.dispatch(gmail().copy(originChain = listOf("m1")), listOf(macro()), false)
        h.engine.dispatch(gmail(key = "x").copy(originChain = listOf("a", "b", "c", "d")), listOf(macro()), false)
        h.engine.dispatch(gmail(key = "y").copy(originChain = listOf("other")), listOf(macro()), false)
        advanceUntilIdle()
        assertEquals(listOf(RunOutcome.SKIPPED, RunOutcome.SKIPPED, RunOutcome.SUCCESS), h.traces.map { it.outcome })
        assertTrue(h.traces[0].reason!!.startsWith("Loop prevented"))
    }

    // ---- execution ------------------------------------------------------------------------------

    @Test
    fun `timeout cancels the run and is recorded`() = runTest {
        val h = EngineHarness(this)
        val m = macro(
            actions = listOf(log("before"), step(WaitAction.ID, "duration" to 600_000), log("after")),
            settings = MacroSettings(maxRunTimeMs = 5_000),
        )
        h.engine.dispatch(gmail(), listOf(m), false)
        advanceUntilIdle()
        val t = h.traces.single()
        assertEquals(RunOutcome.TIMED_OUT, t.outcome)
        assertEquals(listOf("before"), h.log)
        assertEquals(5_000, t.durationMs)
        assertTrue(h.engine.running.value.isEmpty())
    }

    @Test
    fun `failure policy continue vs stop, and exceptions are failures`() = runTest {
        val h = EngineHarness(this)
        h.engine.dispatch(gmail(key = "1"), listOf(macro(actions = listOf(step("test.fail"), step("test.throw"), log("after")))), false)
        advanceUntilIdle()
        assertEquals(RunOutcome.PARTIAL, h.traces.last().outcome)
        assertEquals(listOf("after"), h.log)
        assertTrue(h.traces.last().steps.any { it.detail?.contains("kaboom") == true })

        h.engine.dispatch(gmail(key = "2"), listOf(macro(id = "m2", actions = listOf(step("test.fail", onFailure = FailurePolicy.STOP_MACRO), log("never")))), false)
        advanceUntilIdle()
        assertEquals(RunOutcome.FAILED, h.traces.last().outcome)
        assertEquals(listOf("after"), h.log)
    }

    @Test
    fun `if then otherwise`() = runTest {
        val h = EngineHarness(this)
        val node = ActionNode.If(uid(), all(textContains("urgent")), then = listOf(log("then")), otherwise = listOf(log("else")))
        h.engine.dispatch(whatsapp("A", "urgent", key = "1"), listOf(macro(actions = listOf(node, log("end")))), false)
        h.engine.dispatch(whatsapp("A", "calm", key = "2"), listOf(macro(id = "m2", actions = listOf(node, log("end")))), false)
        advanceUntilIdle()
        assertEquals(listOf("then", "end", "else", "end"), h.log)
    }

    @Test
    fun `repeat runs body n times and is capped`() = runTest {
        val h = EngineHarness(this)
        h.engine.dispatch(gmail(key = "1"), listOf(macro(actions = listOf(ActionNode.Repeat(uid(), 3, listOf(log("x")))))), false)
        advanceUntilIdle()
        assertEquals(3, h.log.size)
        h.log.clear()
        h.engine.dispatch(gmail(key = "2"), listOf(macro(id = "m2", actions = listOf(ActionNode.Repeat(uid(), 1_000_000, listOf(log("x")))))), false)
        advanceUntilIdle()
        assertEquals(ActionNode.Repeat.MAX_REPEAT, h.log.size)
    }

    @Test
    fun `stop macro block ends the run successfully, even inside repeat`() = runTest {
        val h = EngineHarness(this)
        val body = listOf(log("x"), step(StopMacroAction.ID), log("never"))
        h.engine.dispatch(gmail(), listOf(macro(actions = listOf(ActionNode.Repeat(uid(), 5, body), log("never")))), false)
        advanceUntilIdle()
        assertEquals(listOf("x"), h.log)
        assertEquals(RunOutcome.SUCCESS, h.traces.single().outcome)
    }

    @Test
    fun `stop and stop all interrupt running macros`() = runTest {
        val h = EngineHarness(this)
        val long = listOf(step(WaitAction.ID, "duration" to 600_000), log("never"))
        val r = h.engine.dispatch(gmail(key = "1"), listOf(macro(actions = long)), false)
        runCurrent()
        h.engine.stop(r.started.single().runId)
        advanceUntilIdle()
        assertEquals(RunOutcome.STOPPED, h.traces.single().outcome)
        assertEquals("Stopped by you", h.traces.single().reason)

        h.engine.dispatch(gmail(key = "2"), listOf(macro(id = "a", actions = long), macro(id = "b", actions = long)), false)
        runCurrent()
        assertEquals(2, h.engine.running.value.size)
        h.engine.stopAll()
        advanceUntilIdle()
        assertTrue(h.engine.running.value.isEmpty())
        assertEquals(3, h.traces.count { it.outcome == RunOutcome.STOPPED })
        assertTrue(h.log.isEmpty())
    }

    @Test
    fun `missing blocks never crash`() = runTest {
        val h = EngineHarness(this)
        val withMissingAction = macro(actions = listOf(step("action.from_future"), log("after")))
        val withMissingCondition = macro(id = "m2", conditions = all(leaf("condition.from_future", negate = true)))
        val withMissingTrigger = macro(id = "m3").copy(triggers = listOf(inst("trigger.from_future")))
        val unknownNode = macro(id = "m4", actions = listOf(ActionNode.Unknown("u", "parallel", JsonObject(emptyMap())), log("after4")))
        h.engine.dispatch(gmail(), listOf(withMissingAction, withMissingCondition, withMissingTrigger, unknownNode), false)
        advanceUntilIdle()
        val byMacro = h.traces.associateBy { it.macroId }
        assertEquals(RunOutcome.PARTIAL, byMacro["m1"]!!.outcome)
        assertEquals(RunOutcome.CONDITIONS_NOT_MET, byMacro["m2"]!!.outcome) // missing fails even when negated
        assertTrue("m3" !in byMacro)
        assertEquals(RunOutcome.PARTIAL, byMacro["m4"]!!.outcome)
        assertEquals(setOf("after", "after4"), h.log.toSet())
    }

    @Test
    fun `trace contains each condition and group with reasons`() = runTest {
        val h = EngineHarness(this)
        h.engine.dispatch(gmail(), listOf(macro(conditions = any(senderIs("Priya"), senderIs("Rahul Sharma")))), false)
        advanceUntilIdle()
        val steps = h.traces.single().steps
        val group = steps.single { it.kind == StepKind.GROUP }
        assertEquals("any of 2", group.label)
        assertEquals("1 of 2 passed", group.detail)
        // Group header comes before its children, children are indented.
        val gi = steps.indexOf(group)
        assertTrue(steps.drop(gi + 1).take(2).all { it.kind == StepKind.CONDITION && it.depth == 1 })
    }
}
