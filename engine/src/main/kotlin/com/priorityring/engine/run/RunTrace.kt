package com.priorityring.engine.run

import kotlinx.serialization.Serializable

/** How a run (or a non-run) ended. Stored as its lowercase name. */
@Serializable
enum class RunOutcome(val label: String) {
    /** Every action succeeded (or the macro ended with a Stop block). */
    SUCCESS("ran"),

    /** Ran to the end, but at least one action failed (failure policy "continue"). */
    PARTIAL("ran with errors"),

    /** An action failed with failure policy "stop macro", or the run crashed. */
    FAILED("failed"),

    /** A trigger matched but the conditions didn't pass. */
    CONDITIONS_NOT_MET("not met"),

    /** A trigger matched but a gate (paused, duplicate, cooldown, …) prevented the run. */
    SKIPPED("skipped"),

    /** Hit the macro's max run time and was cancelled. */
    TIMED_OUT("timed out"),

    /** Stopped by the user (Home / notification / "stop all"). */
    STOPPED("stopped"),
    ;

    val storageName: String get() = name.lowercase()
}

@Serializable
enum class StepKind { TRIGGER, GATE, GROUP, CONDITION, ACTION, CONTROL, LOG }

@Serializable
enum class StepStatus { OK, FAILED, SKIPPED, INFO }

/**
 * One line of a run's step-by-step trace. [depth] is the nesting level (conditions inside groups,
 * actions inside If / Repeat), so the UI can indent.
 */
@Serializable
data class TraceStep(
    val kind: StepKind,
    val depth: Int,
    val label: String,
    val status: StepStatus,
    val detail: String? = null,
    val durationMs: Long? = null,
    val atMs: Long,
    /** The block instance this step belongs to, when there is one. */
    val uid: String? = null,
)

/**
 * The full record of one trigger match: why it ran or didn't, and what every step did. This is the
 * Activity screen's data and the main debugging tool.
 */
@Serializable
data class RunTrace(
    val runId: String,
    val macroId: String,
    val macroName: String,
    val eventType: String,
    /** Event variables at trigger time (privacy-filtered before storage, see [TracePrivacy]). */
    val variables: Map<String, String?> = emptyMap(),
    val startedAtMs: Long,
    val finishedAtMs: Long? = null,
    val outcome: RunOutcome,
    /** One-line explanation for skips / failures / stops. */
    val reason: String? = null,
    val steps: List<TraceStep> = emptyList(),
    val simulated: Boolean = false,
) {
    val durationMs: Long? get() = finishedAtMs?.minus(startedAtMs)
}
