package com.priorityring.engine

import com.priorityring.engine.block.ActionBlock
import com.priorityring.engine.block.ActionContext
import com.priorityring.engine.block.ActionResult
import com.priorityring.engine.block.BlockCategory
import com.priorityring.engine.block.BlockConfig
import com.priorityring.engine.block.BlockKind
import com.priorityring.engine.block.BlockMeta
import com.priorityring.engine.block.BlockRegistry
import com.priorityring.engine.block.ConfigField
import com.priorityring.engine.blocks.core.CoreBlocks
import com.priorityring.engine.blocks.core.NotificationPostedTrigger
import com.priorityring.engine.blocks.core.TextMatchCondition
import com.priorityring.engine.event.TriggerEvent
import com.priorityring.engine.model.ActionNode
import com.priorityring.engine.model.BlockInstance
import com.priorityring.engine.model.ConditionNode
import com.priorityring.engine.model.FailurePolicy
import com.priorityring.engine.model.LogicOp
import com.priorityring.engine.model.Macro
import com.priorityring.engine.model.MacroSettings
import com.priorityring.engine.notification.KnownApps
import com.priorityring.engine.notification.NotificationParsers
import com.priorityring.engine.notification.RawMessage
import com.priorityring.engine.notification.RawNotification
import com.priorityring.engine.run.EngineClock
import com.priorityring.engine.run.MacroEngine
import com.priorityring.engine.run.RunRecorder
import com.priorityring.engine.run.RunTrace
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.JsonObject
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/** Shared fixtures for engine tests: fake blocks, builders, and a virtual-time engine. */

/** Appends its rendered "msg" to [log]. */
class LogAction(val log: MutableList<String>) : ActionBlock {
    override val meta = BlockMeta(
        "test.log", BlockKind.ACTION, "Log", "", BlockCategory.CONTROL, "x",
        fields = listOf(ConfigField.Text("msg", "Message", variables = true)),
    )
    override fun summarize(config: BlockConfig) = "Log “${config.string("msg")}”"
    override suspend fun execute(config: BlockConfig, context: ActionContext): ActionResult {
        log += context.render(config.string("msg"))
        return ActionResult.Success()
    }
}

object FailAction : ActionBlock {
    override val meta = BlockMeta("test.fail", BlockKind.ACTION, "Fail", "", BlockCategory.CONTROL, "x")
    override fun summarize(config: BlockConfig) = "Fail"
    override suspend fun execute(config: BlockConfig, context: ActionContext) = ActionResult.Failure("boom")
}

object ThrowAction : ActionBlock {
    override val meta = BlockMeta("test.throw", BlockKind.ACTION, "Throw", "", BlockCategory.CONTROL, "x")
    override fun summarize(config: BlockConfig) = "Throw"
    override suspend fun execute(config: BlockConfig, context: ActionContext): ActionResult = error("kaboom")
}

/** Waits `ms` and logs "slept". */
class SleepAction(val log: MutableList<String>) : ActionBlock {
    override val meta = BlockMeta(
        "test.sleep", BlockKind.ACTION, "Sleep", "", BlockCategory.CONTROL, "x",
        fields = listOf(ConfigField.Number("ms", "ms", 1_000)),
    )
    override fun summarize(config: BlockConfig) = "Sleep ${config.long("ms")}"
    override suspend fun execute(config: BlockConfig, context: ActionContext): ActionResult {
        delay(config.long("ms"))
        log += "slept"
        return ActionResult.Success()
    }
}

private val uidCounter = AtomicInteger()
fun uid() = "u${uidCounter.incrementAndGet()}"

fun inst(blockId: String, vararg cfg: Pair<String, Any>) =
    BlockInstance(uid(), blockId, JsonObject(cfg.associate { (k, v) -> k to v.toJson() }))

fun step(blockId: String, vararg cfg: Pair<String, Any>, onFailure: FailurePolicy = FailurePolicy.CONTINUE) =
    ActionNode.Step(inst(blockId, *cfg), onFailure)

fun log(msg: String, onFailure: FailurePolicy = FailurePolicy.CONTINUE) = step("test.log", "msg" to msg, onFailure = onFailure)

fun leaf(blockId: String, vararg cfg: Pair<String, Any>, negate: Boolean = false) =
    ConditionNode.Leaf(inst(blockId, *cfg), negate)

fun senderIs(name: String, negate: Boolean = false) =
    leaf(TextMatchCondition.ID, "variable" to "sender", "mode" to "exact", "pattern" to name, negate = negate)

fun textContains(s: String) = leaf(TextMatchCondition.ID, "variable" to "text", "pattern" to s)

fun all(vararg c: ConditionNode, negate: Boolean = false) = ConditionNode.Group(LogicOp.AND, c.toList(), negate)
fun any(vararg c: ConditionNode, negate: Boolean = false) = ConditionNode.Group(LogicOp.OR, c.toList(), negate)

fun macro(
    id: String = "m1",
    conditions: ConditionNode.Group = ConditionNode.Group(),
    actions: List<ActionNode> = listOf(log("ran")),
    settings: MacroSettings = MacroSettings(maxRunsPerHour = 1000),
    apps: List<String> = emptyList(),
    enabled: Boolean = true,
) = Macro(
    id = id, name = "Macro $id", enabled = enabled,
    triggers = listOf(inst(NotificationPostedTrigger.ID, "apps" to apps)),
    conditions = conditions, actions = actions, settings = settings,
)

/** A parsed notification event, exactly as the Android listener would produce it. */
fun gmail(sender: String = "Rahul Sharma", subject: String = "Quarterly report", key: String = "0|gm|1"): TriggerEvent =
    NotificationParsers.toEvent(
        RawNotification(
            packageName = KnownApps.GMAIL, key = key, postTimeMs = 0, appLabel = "Gmail",
            title = sender, text = subject, bigText = "$subject\nPlease see attached.", subText = "me@gmail.com",
        ),
    )

fun whatsapp(sender: String, text: String, key: String = "0|wa|1"): TriggerEvent =
    NotificationParsers.toEvent(
        RawNotification(
            packageName = KnownApps.WHATSAPP, key = key, postTimeMs = 0, appLabel = "WhatsApp",
            title = sender, messages = listOf(RawMessage(sender, text, 1)), isGroupConversation = false,
        ),
    )

class Recorder : RunRecorder {
    val traces = mutableListOf<RunTrace>()
    override suspend fun record(trace: RunTrace) { traces += trace }
}

/** Engine on virtual time: `delay`s are instant, and the clock follows the test scheduler. */
class EngineHarness(val scope: TestScope) {
    val log = mutableListOf<String>()
    val recorder = Recorder()
    val clock = object : EngineClock {
        override fun nowMillis() = BASE + scope.testScheduler.currentTime
        override fun zone(): ZoneId = ZoneId.of("Asia/Kolkata")
    }
    val registry = BlockRegistry(CoreBlocks.all + listOf(LogAction(log), FailAction, ThrowAction, SleepAction(log)))
    // Runs are children of the test, so advanceUntilIdle() drives them and runTest waits for them.
    // (Each run catches its own failures, so one bad run can't fail the test scope.)
    val engine = MacroEngine(registry, scope, recorder, clock)

    val traces get() = recorder.traces

    companion object {
        /** 2026-10-27 12:00 IST (a Tuesday). */
        const val BASE = 1_793_082_600_000L
    }
}
