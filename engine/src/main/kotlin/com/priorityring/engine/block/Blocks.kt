package com.priorityring.engine.block

import com.priorityring.engine.event.TriggerEvent
import com.priorityring.engine.variables.Variables
import java.time.ZonedDateTime

/**
 * The contract every block implements. A block is a stateless singleton: all per-use state lives
 * in its [BlockConfig] and the context passed to each call.
 *
 * To add a block: implement one of [TriggerBlock], [ConditionBlock] or [ActionBlock] in one file
 * and register it in the block catalogue. The engine, database and builder UI need no changes.
 */
sealed interface Block {
    val meta: BlockMeta

    /** Plain-English one-liner for cards, e.g. "Ring Morning Alarm for up to 2 min". */
    fun summarize(config: BlockConfig): String

    /** Problems the builder should show before saving. Defaults to schema validation. */
    fun validate(config: BlockConfig): List<String> = config.validate()

    /**
     * Block-specific availability on top of [BlockMeta.requirements] and SDK bounds — e.g. "this
     * phone has no flash". Return null when there's nothing extra to say.
     */
    fun extraAvailability(env: DeviceCapabilities): Availability? = null
}

interface TriggerBlock : Block {
    /** Normalised event types this trigger listens to, e.g. "notification.posted". */
    val eventTypes: Set<String>

    /** Decide whether [event] fires this trigger. Must be fast and must not throw. */
    fun match(config: BlockConfig, event: TriggerEvent): TriggerMatch
}

interface ConditionBlock : Block {
    suspend fun evaluate(config: BlockConfig, context: EvalContext): ConditionResult
}

interface ActionBlock : Block {
    suspend fun execute(config: BlockConfig, context: ActionContext): ActionResult
}

// ---- Results --------------------------------------------------------------------------------

/** [reason] is shown in the run trace whether or not it matched, so make it specific. */
data class TriggerMatch(val matched: Boolean, val reason: String) {
    companion object {
        fun yes(reason: String) = TriggerMatch(true, reason)
        fun no(reason: String) = TriggerMatch(false, reason)
    }
}

data class ConditionResult(val passed: Boolean, val reason: String) {
    companion object {
        fun pass(reason: String) = ConditionResult(true, reason)
        fun fail(reason: String) = ConditionResult(false, reason)
    }
}

sealed interface ActionResult {
    /** [detail] says what actually happened, e.g. "Opened via notification (background launch blocked)". */
    data class Success(val detail: String? = null) : ActionResult

    data class Failure(val reason: String) : ActionResult

    /** Ends the macro run normally (the "Stop macro" block). */
    data class Stop(val reason: String = "Stopped by action") : ActionResult
}

// ---- Contexts -------------------------------------------------------------------------------

/** What a condition can see while being evaluated. */
interface EvalContext {
    val event: TriggerEvent
    val variables: Variables

    /** Current local time; injected so tests can control it. */
    fun now(): ZonedDateTime
}

/** What an action can see and do while running. */
interface ActionContext : EvalContext {
    val macroId: String
    val macroName: String
    val runId: String

    /** Substitutes `{variable}` references using this run's variables. */
    fun render(template: String): String = variables.render(template)

    /** Adds a line to this run's trace. */
    fun log(message: String)
}

// ---- Availability ---------------------------------------------------------------------------

sealed interface Availability {
    data object Available : Availability

    /** Fixable by the user; the UI offers a button per requirement. */
    data class NeedsPermission(val missing: List<Requirement>) : Availability

    /** Can't work on this device; [reason] is shown in the library. */
    data class Unsupported(val reason: String) : Availability
}

/**
 * What the current device can do, provided by the Android layer. Keeps availability logic pure and
 * testable.
 */
interface DeviceCapabilities {
    val sdkInt: Int
    fun isGranted(requirement: Requirement): Boolean
    fun hasFeature(feature: String): Boolean = true
}

/** Combines SDK bounds, requirements and the block's own check into one answer. */
fun Block.availability(env: DeviceCapabilities): Availability {
    meta.minSdk?.let { if (env.sdkInt < it) return Availability.Unsupported("Needs Android API $it or newer") }
    meta.maxSdk?.let { if (env.sdkInt > it) return Availability.Unsupported("Not supported after Android API $it") }
    if (meta.tier == Tier.ROOT_ONLY) return Availability.Unsupported("Not possible without root")
    extraAvailability(env)?.let { if (it !is Availability.Available) return it }
    val missing = meta.requirements.filterNot(env::isGranted)
    return if (missing.isEmpty()) Availability.Available else Availability.NeedsPermission(missing)
}
