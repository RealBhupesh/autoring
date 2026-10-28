package com.priorityring.engine.run

import com.priorityring.engine.block.ActionContext
import com.priorityring.engine.block.ActionResult
import com.priorityring.engine.block.BlockConfig
import com.priorityring.engine.block.BlockRegistry
import com.priorityring.engine.block.EvalContext
import com.priorityring.engine.block.TriggerBlock
import com.priorityring.engine.block.TriggerMatch
import com.priorityring.engine.block.formatDuration
import com.priorityring.engine.event.TriggerEvent
import com.priorityring.engine.model.ActionNode
import com.priorityring.engine.model.BlockInstance
import com.priorityring.engine.model.ConditionNode
import com.priorityring.engine.model.FailurePolicy
import com.priorityring.engine.model.LogicOp
import com.priorityring.engine.model.Macro
import com.priorityring.engine.variables.BuiltinVariables
import com.priorityring.engine.variables.Variables
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** Receives every run and skip, e.g. to save it to the database. */
fun interface RunRecorder {
    suspend fun record(trace: RunTrace)
}

data class RunningMacro(val runId: String, val macroId: String, val macroName: String, val startedAtMs: Long)

/** What happened to one event. Mostly useful for the simulator and tests. */
data class DispatchResult(
    /** Runs that were started (they finish asynchronously). */
    val started: List<RunningMacro>,
    /** Traces of macros whose trigger matched but which didn't run (skips and conditions not met). */
    val notRun: List<RunTrace>,
) {
    val matchedAny: Boolean get() = started.isNotEmpty() || notRun.isNotEmpty()
}

data class EngineLimits(
    /** How long an event key is remembered for dedupe. */
    val dedupeWindowMs: Long = 24 * 60 * 60_000L,
    val maxDedupeKeys: Int = 2_000,
    /** Max macro→event→macro hops before an event is treated as a loop. */
    val maxOriginChain: Int = 4,
    /** Hard ceiling regardless of macro settings. */
    val maxRunTimeCapMs: Long = 60 * 60_000L,
)

/**
 * The heart of PriorityRing: given an event and the user's macros, decides which macros run, runs
 * them, and records a trace for each.
 *
 * Order of checks for each enabled macro whose trigger matches:
 * 1. paused (master switch) · duplicate event · loop prevention          → skipped
 * 2. conditions                                                            → not met
 * 3. already running · cooldown · rate limit (checked atomically with 4)   → skipped
 * 4. start the run in [scope], with a timeout; actions run in order.
 *
 * Runs are independent coroutines, so one slow macro never delays another.
 */
class MacroEngine(
    private val registry: BlockRegistry,
    private val scope: CoroutineScope,
    private val recorder: RunRecorder,
    private val clock: EngineClock = EngineClock.System,
    private val builtins: BuiltinVariables = BuiltinVariables { null },
    private val limits: EngineLimits = EngineLimits(),
) {
    private val mutex = Mutex()

    // All of the below are guarded by [mutex].
    private val seenEvents = LinkedHashMap<String, Long>()
    private val lastRunStart = HashMap<String, Long>()
    private val recentStarts = HashMap<String, ArrayDeque<Long>>()
    private val jobs = HashMap<String, Job>() // runId → job
    private val stopReasons = HashMap<String, String>()

    private val _running = MutableStateFlow<List<RunningMacro>>(emptyList())
    val running: StateFlow<List<RunningMacro>> = _running.asStateFlow()

    /** Lets cooldowns survive process restarts: call with each macro's stored last-run time. */
    suspend fun seedLastRun(macroId: String, atMs: Long) = mutex.withLock {
        lastRunStart.merge(macroId, atMs, ::maxOf)
    }

    suspend fun dispatch(event: TriggerEvent, macros: List<Macro>, paused: Boolean): DispatchResult {
        val now = clock.nowMillis()

        // 1. Which enabled macros does this event trigger?
        val matches = macros.filter { it.enabled }.mapNotNull { m -> firstMatchingTrigger(m, event)?.let { m to it } }
        if (matches.isEmpty()) return DispatchResult(emptyList(), emptyList())

        // Dedupe is per event (not per macro): the same notification never triggers twice.
        val duplicate = event.dedupeKey?.let { key -> mutex.withLock { checkAndRememberEvent(key, now) } } ?: false

        val started = mutableListOf<RunningMacro>()
        val notRun = mutableListOf<RunTrace>()
        for ((macro, trigger) in matches) {
            val base = TraceBuilder(clock, macro, event, UUID.randomUUID().toString())
            base.triggerStep(trigger)

            val earlySkip = when {
                paused -> "Paused: the master switch is off"
                duplicate -> "Duplicate: this exact notification was already handled"
                macro.id in event.originChain -> "Loop prevented: this macro caused the event"
                event.originChain.size >= limits.maxOriginChain ->
                    "Loop prevented: event caused by a chain of ${event.originChain.size} macros"
                else -> null
            }
            if (earlySkip != null) {
                notRun += base.skip(earlySkip).also { recorder.record(it) }
                continue
            }

            // 2. Conditions.
            val variables = Variables(event.variables, builtins)
            val evalCtx = SimpleEvalContext(event, variables, clock)
            val passed = evalGroup(macro.conditions, 0, evalCtx, base, root = true)
            if (!passed) {
                notRun += base.finish(RunOutcome.CONDITIONS_NOT_MET, "Conditions not met").also { recorder.record(it) }
                continue
            }

            // 3 + 4. Concurrency gates and registration must be atomic.
            val gate = mutex.withLock { gateAndRegister(macro, base.runId, now) }
            if (gate != null) {
                notRun += base.skip(gate).also { recorder.record(it) }
                continue
            }
            started += launchRun(macro, base, variables)
        }
        return DispatchResult(started, notRun)
    }

    /** Stops one run immediately. Its trace is recorded as STOPPED. */
    suspend fun stop(runId: String, reason: String = "Stopped by you") {
        val job = mutex.withLock {
            stopReasons[runId] = reason
            jobs[runId]
        }
        job?.cancel(CancellationException(reason))
    }

    suspend fun stopAll(reason: String = "Stopped by you (stop all)") {
        val all = mutex.withLock {
            jobs.keys.forEach { stopReasons[it] = reason }
            jobs.values.toList()
        }
        all.forEach { it.cancel(CancellationException(reason)) }
    }

    // ---- matching & gates ---------------------------------------------------------------------

    private fun firstMatchingTrigger(macro: Macro, event: TriggerEvent): Pair<BlockInstance, TriggerMatch>? {
        for (inst in macro.triggers) {
            val block = registry.trigger(inst.blockId) ?: continue // missing block: never matches
            if (event.type !in block.eventTypes) continue
            val match = runCatching { block.match(BlockConfig.of(inst.config, block.meta), event) }
                .getOrElse { TriggerMatch.no("Trigger error: ${it.message}") }
            if (match.matched) return inst to match
        }
        return null
    }

    /** Returns true if [key] was already seen within the window; remembers it either way. */
    private fun checkAndRememberEvent(key: String, now: Long): Boolean {
        val iter = seenEvents.entries.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            if (now - e.value > limits.dedupeWindowMs || seenEvents.size > limits.maxDedupeKeys) iter.remove() else break
        }
        val seen = seenEvents.containsKey(key)
        seenEvents.remove(key)
        seenEvents[key] = now // re-insert at the end: insertion order == age order
        return seen
    }

    private fun gateAndRegister(macro: Macro, runId: String, now: Long): String? {
        val s = macro.settings
        if (s.skipIfRunning && _running.value.any { it.macroId == macro.id }) {
            return "Already running (\"don't run again while running\" is on)"
        }
        val last = lastRunStart[macro.id]
        if (s.cooldownMs > 0 && last != null && now - last < s.cooldownMs) {
            return "Cooldown: ${formatDuration(s.cooldownMs - (now - last))} left of ${formatDuration(s.cooldownMs)}"
        }
        val starts = recentStarts.getOrPut(macro.id) { ArrayDeque() }
        while (starts.isNotEmpty() && now - starts.first() > HOUR_MS) starts.removeFirst()
        if (s.maxRunsPerHour > 0 && starts.size >= s.maxRunsPerHour) {
            return "Rate limit: already ran ${starts.size} times in the last hour"
        }
        starts.addLast(now)
        lastRunStart[macro.id] = now
        val info = RunningMacro(runId, macro.id, macro.name, now)
        _running.update { it + info }
        return null
    }

    // ---- running --------------------------------------------------------------------------------

    private suspend fun launchRun(macro: Macro, trace: TraceBuilder, variables: Variables): RunningMacro {
        val info = _running.value.first { it.runId == trace.runId }
        val timeout = macro.settings.maxRunTimeMs.coerceIn(1_000, limits.maxRunTimeCapMs)
        // LAZY so the job is registered before it can possibly finish and unregister itself.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var outcome: RunOutcome
            var reason: String? = null
            try {
                val ctx = RunContext(macro, trace, variables, clock)
                val result = withTimeout(timeout) { runList(macro.actions, 0, ctx) }
                when (result) {
                    is Flow.Failed -> { outcome = RunOutcome.FAILED; reason = result.reason }
                    is Flow.Stopped -> { outcome = if (ctx.anyFailed) RunOutcome.PARTIAL else RunOutcome.SUCCESS; reason = result.reason }
                    Flow.Continue -> outcome = if (ctx.anyFailed) RunOutcome.PARTIAL else RunOutcome.SUCCESS
                }
                if (outcome == RunOutcome.PARTIAL && reason == null) reason = "Some actions failed"
            } catch (e: TimeoutCancellationException) {
                outcome = RunOutcome.TIMED_OUT
                reason = "Exceeded max run time of ${formatDuration(timeout)}"
                trace.add(StepKind.CONTROL, 0, "Timed out", StepStatus.FAILED, reason)
            } catch (e: CancellationException) {
                outcome = RunOutcome.STOPPED
                // We're cancelled, so any suspending call must opt out of cancellation.
                withContext(NonCancellable) {
                    reason = mutex.withLock { stopReasons[trace.runId] } ?: e.message ?: "Cancelled"
                    finishRun(trace, outcome, reason)
                }
                throw e
            } catch (e: Throwable) {
                outcome = RunOutcome.FAILED
                reason = "Crashed: ${e.message ?: e::class.simpleName}"
            }
            withContext(NonCancellable) { finishRun(trace, outcome, reason) }
        }
        mutex.withLock { jobs[trace.runId] = job }
        job.start()
        return info
    }

    private suspend fun finishRun(trace: TraceBuilder, outcome: RunOutcome, reason: String?) {
        mutex.withLock {
            jobs.remove(trace.runId)
            stopReasons.remove(trace.runId)
            _running.update { list -> list.filterNot { it.runId == trace.runId } }
        }
        recorder.record(trace.finish(outcome, reason))
    }

    private sealed interface Flow {
        data object Continue : Flow
        data class Stopped(val reason: String) : Flow
        data class Failed(val reason: String) : Flow
    }

    private suspend fun runList(list: List<ActionNode>, depth: Int, ctx: RunContext): Flow {
        for (node in list) {
            val flow = runNode(node, depth, ctx)
            if (flow != Flow.Continue) return flow
        }
        return Flow.Continue
    }

    private suspend fun runNode(node: ActionNode, depth: Int, ctx: RunContext): Flow = when (node) {
        is ActionNode.Step -> runStep(node, depth, ctx)

        is ActionNode.If -> {
            val passed = evalGroup(node.condition, depth + 1, ctx, ctx.trace, root = false, header = "If")
            ctx.trace.add(StepKind.CONTROL, depth, if (passed) "Then" else "Otherwise", StepStatus.INFO, uid = node.uid)
            runList(if (passed) node.then else node.otherwise, depth + 1, ctx)
        }

        is ActionNode.Repeat -> {
            val n = node.count.coerceIn(1, ActionNode.Repeat.MAX_REPEAT)
            var result: Flow = Flow.Continue
            for (i in 1..n) {
                ctx.trace.add(StepKind.CONTROL, depth, "Repeat $i of $n", StepStatus.INFO, uid = node.uid)
                result = runList(node.body, depth + 1, ctx)
                if (result != Flow.Continue) break
            }
            result
        }

        is ActionNode.Unknown -> {
            ctx.anyFailed = true
            ctx.trace.add(StepKind.ACTION, depth, "Unknown step (${node.type})", StepStatus.FAILED,
                "Made with a newer version of PriorityRing", uid = node.uid)
            Flow.Continue
        }
    }

    private suspend fun runStep(node: ActionNode.Step, depth: Int, ctx: RunContext): Flow {
        val inst = node.block
        val block = registry.action(inst.blockId)
        val label = registry.summarize(inst.blockId, inst.config)
        val started = clock.nowMillis()
        val result = if (block == null) {
            ActionResult.Failure("Missing block: ${inst.blockId}")
        } else {
            try {
                block.execute(BlockConfig.of(inst.config, block.meta), ctx.forStep(inst.uid, depth))
            } catch (e: CancellationException) {
                ctx.trace.add(StepKind.ACTION, depth, label, StepStatus.SKIPPED, "Interrupted",
                    clock.nowMillis() - started, inst.uid)
                throw e
            } catch (e: Throwable) {
                ActionResult.Failure("Error: ${e.message ?: e::class.simpleName}")
            }
        }
        val took = clock.nowMillis() - started
        return when (result) {
            is ActionResult.Success -> {
                ctx.trace.add(StepKind.ACTION, depth, label, StepStatus.OK, result.detail, took, inst.uid)
                Flow.Continue
            }
            is ActionResult.Stop -> {
                ctx.trace.add(StepKind.ACTION, depth, label, StepStatus.OK, result.reason, took, inst.uid)
                Flow.Stopped(result.reason)
            }
            is ActionResult.Failure -> {
                ctx.anyFailed = true
                val stop = node.onFailure == FailurePolicy.STOP_MACRO
                ctx.trace.add(StepKind.ACTION, depth, label, StepStatus.FAILED,
                    result.reason + if (stop) " · stopping macro" else " · continuing", took, inst.uid)
                if (stop) Flow.Failed("“$label” failed: ${result.reason}") else Flow.Continue
            }
        }
    }

    // ---- conditions -----------------------------------------------------------------------------

    /**
     * Evaluates every node (no short-circuit) so the trace explains each condition, not just the
     * first failing one. Conditions must be side-effect free, so this is safe.
     */
    private suspend fun evalGroup(
        group: ConditionNode.Group,
        depth: Int,
        ctx: EvalContext,
        trace: TraceBuilder,
        root: Boolean,
        header: String? = null,
    ): Boolean {
        if (group.children.isEmpty()) {
            if (root) trace.add(StepKind.GROUP, depth, "No conditions", StepStatus.INFO)
            return !group.negate
        }
        val slot = trace.reserve()
        val results = group.children.map { evalNode(it, depth + 1, ctx, trace) }
        val raw = if (group.op == LogicOp.AND) results.all { it } else results.any { it }
        val passed = raw != group.negate
        val what = buildString {
            if (header != null) append("$header: ")
            if (group.negate) append("NOT ")
            append(if (group.op == LogicOp.AND) "all of" else "any of")
            append(" ${group.children.size}")
        }
        trace.fill(slot, StepKind.GROUP, depth, what, if (passed) StepStatus.OK else StepStatus.FAILED,
            "${results.count { it }} of ${results.size} passed")
        return passed
    }

    private suspend fun evalNode(node: ConditionNode, depth: Int, ctx: EvalContext, trace: TraceBuilder): Boolean =
        when (node) {
            is ConditionNode.Group -> evalGroup(node, depth, ctx, trace, root = false)
            is ConditionNode.Unknown -> {
                trace.add(StepKind.CONDITION, depth, "Unknown condition (${node.type})", StepStatus.FAILED,
                    "Made with a newer version of PriorityRing")
                false
            }
            is ConditionNode.Leaf -> {
                val inst = node.block
                val block = registry.condition(inst.blockId)
                val label = registry.summarize(inst.blockId, inst.config).let { if (node.negate) "NOT ($it)" else it }
                val (raw, reason) = if (block == null) {
                    false to "Missing block: ${inst.blockId}"
                } else {
                    try {
                        block.evaluate(BlockConfig.of(inst.config, block.meta), ctx).let { it.passed to it.reason }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        false to "Error: ${e.message ?: e::class.simpleName}"
                    }
                }
                // A missing/broken block fails even when negated: never run on a guess.
                val passed = if (block == null) false else raw != node.negate
                trace.add(StepKind.CONDITION, depth, label, if (passed) StepStatus.OK else StepStatus.FAILED, reason, uid = inst.uid)
                passed
            }
        }

    // ---- contexts -------------------------------------------------------------------------------

    private class SimpleEvalContext(
        override val event: TriggerEvent,
        override val variables: Variables,
        private val clock: EngineClock,
    ) : EvalContext {
        override fun now() = clock.now()
    }

    private class RunContext(
        val macro: Macro,
        val trace: TraceBuilder,
        override val variables: Variables,
        private val clock: EngineClock,
    ) : EvalContext {
        override val event: TriggerEvent get() = trace.event
        override fun now() = clock.now()
        var anyFailed = false

        fun forStep(uid: String, depth: Int): ActionContext = object : ActionContext {
            override val event get() = this@RunContext.event
            override val variables get() = this@RunContext.variables
            override fun now() = clock.now()
            override val macroId get() = macro.id
            override val macroName get() = macro.name
            override val runId get() = trace.runId
            override fun log(message: String) =
                trace.add(StepKind.LOG, depth + 1, message, StepStatus.INFO, uid = uid)
        }
    }

    private companion object {
        const val HOUR_MS = 60 * 60_000L
    }
}

/** Accumulates steps for one run. Thread-safe enough for one coroutine plus log() calls. */
internal class TraceBuilder(
    private val clock: EngineClock,
    private val macro: Macro,
    val event: TriggerEvent,
    val runId: String,
) {
    private val startedAt = clock.nowMillis()
    private val steps = ArrayList<TraceStep?>()

    fun triggerStep(match: Pair<BlockInstance, TriggerMatch>) {
        add(StepKind.TRIGGER, 0, "Triggered", StepStatus.OK, match.second.reason, uid = match.first.uid)
    }

    @Synchronized
    fun add(kind: StepKind, depth: Int, label: String, status: StepStatus, detail: String? = null, durationMs: Long? = null, uid: String? = null) {
        steps += TraceStep(kind, depth, label, status, detail, durationMs, clock.nowMillis(), uid)
    }

    /** Reserve a position (e.g. for a group header whose result is known only after its children). */
    @Synchronized
    fun reserve(): Int { steps += null; return steps.lastIndex }

    @Synchronized
    fun fill(slot: Int, kind: StepKind, depth: Int, label: String, status: StepStatus, detail: String?) {
        steps[slot] = TraceStep(kind, depth, label, status, detail, null, clock.nowMillis())
    }

    fun skip(reason: String): RunTrace {
        add(StepKind.GATE, 0, "Skipped", StepStatus.SKIPPED, reason)
        return finish(RunOutcome.SKIPPED, reason)
    }

    @Synchronized
    fun finish(outcome: RunOutcome, reason: String?): RunTrace = RunTrace(
        runId = runId,
        macroId = macro.id,
        macroName = macro.name,
        eventType = event.type,
        variables = event.variables,
        startedAtMs = startedAt,
        finishedAtMs = clock.nowMillis(),
        outcome = outcome,
        reason = reason,
        steps = steps.filterNotNull(),
        simulated = event.simulated,
    )
}
