package com.priorityring.engine.alert

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** Who asked for the alert; shown on the alarm screen and notification. */
@Serializable
data class AlertSource(
    val runId: String,
    val macroId: String,
    val macroName: String,
    /** e.g. "Rahul Sharma" */
    val title: String,
    /** e.g. "Gmail" */
    val app: String? = null,
    /** Short preview of the message, if the macro chose to include one. */
    val preview: String? = null,
)

@Serializable
data class SoundSpec(
    /** Content URI of the sound; empty = system default alarm sound. */
    val uri: String = "",
    /** Temporarily raise the alarm stream to max, restored when the alert ends. */
    val fullVolume: Boolean = true,
)

/** Vibration patterns: alternating off/on durations in ms (Android waveform format), looped. */
@Serializable
enum class VibrationPattern(val label: String, val timings: List<Long>) {
    ALARM("Alarm", listOf(0, 800, 400)),
    HEARTBEAT("Heartbeat", listOf(0, 150, 100, 150, 700)),
    SHORT("Short", listOf(0, 200, 300)),
    LONG("Long", listOf(0, 1500, 500)),
    SOS("SOS", listOf(0, 150, 100, 150, 100, 150, 300, 450, 100, 450, 100, 450, 300, 150, 100, 150, 100, 150, 900)),
}

/** Torch patterns: on/off durations in ms; steady = on the whole time. */
@Serializable
enum class TorchPattern(val label: String, val onMs: Long, val offMs: Long) {
    STEADY("Steady", 0, 0),
    BLINK("Blink", 500, 500),
    STROBE("Strobe", 100, 100),
}

/** What the alert should do right now. Each channel is optional. */
@Serializable
data class AlertChannels(
    val sound: SoundSpec? = null,
    val vibration: VibrationPattern? = null,
    val torch: TorchPattern? = null,
) {
    val isEmpty: Boolean get() = sound == null && vibration == null && torch == null

    /** Union: an already-active channel keeps its settings; missing ones are added. */
    operator fun plus(other: AlertChannels) = AlertChannels(
        sound = sound ?: other.sound,
        vibration = vibration ?: other.vibration,
        torch = torch ?: other.torch,
    )
}

@Serializable
data class AlertRequest(
    val source: AlertSource,
    val channels: AlertChannels,
    /** Each ringing period stops by itself after this long. */
    val maxDurationMs: Long = 2 * 60_000L,
    val snoozeMs: Long = 5 * 60_000L,
    val maxSnoozes: Int = 3,
    /** Ask for the full-screen alarm screen (falls back to a heads-up notification if not allowed). */
    val fullScreen: Boolean = true,
)

@Serializable
enum class AlertPhase { RINGING, SNOOZED }

/** The single source of truth for "is something ringing". Persisted so it survives process death. */
@Serializable
sealed interface AlertState {
    @Serializable
    data object Idle : AlertState

    @Serializable
    data class Active(
        val alertId: String,
        val phase: AlertPhase,
        val sources: List<AlertSource>,
        val channels: AlertChannels,
        val startedAtMs: Long,
        /** When the current ringing period times out (RINGING only). */
        val ringUntilMs: Long,
        /** When ringing resumes (SNOOZED only). */
        val resumeAtMs: Long? = null,
        val ringDurationMs: Long,
        val snoozeMs: Long,
        val maxSnoozes: Int,
        val snoozeCount: Int = 0,
        val fullScreen: Boolean,
    ) : AlertState {
        val snoozesLeft: Int get() = (maxSnoozes - snoozeCount).coerceAtLeast(0)
        val isRinging: Boolean get() = phase == AlertPhase.RINGING
    }
}

/** When the controller next needs to call [AlertMachine.tick], or null if never. */
fun AlertState.nextDeadline(): Long? = when (this) {
    AlertState.Idle -> null
    is AlertState.Active -> if (phase == AlertPhase.RINGING) ringUntilMs else resumeAtMs
}

enum class RequestOutcome { STARTED, JOINED, RESUMED_FROM_SNOOZE }

data class Transition(val state: AlertState, val note: String, val requestOutcome: RequestOutcome? = null)

/**
 * Pure state machine for alerts. No timers, no Android: give it the current state, an event and the
 * current time, get the next state. The Android controller persists the state, schedules a tick for
 * [nextDeadline], and makes the real outputs (sound, vibration, torch) match it.
 *
 * Rules (from the behaviour spec):
 * - Only one alert exists at a time; a second request *joins* it (adds its source and any missing
 *   channels) instead of starting new audio.
 * - A request while snoozed rings again immediately.
 * - Snooze silences now and re-rings after the snooze length, at most `maxSnoozes` times; snoozing
 *   with none left stops the alert.
 * - Each ringing period times out on its own; stop ends everything immediately.
 */
object AlertMachine {

    fun request(state: AlertState, req: AlertRequest, now: Long, newId: () -> String): Transition = when (state) {
        AlertState.Idle -> {
            val s = AlertState.Active(
                alertId = newId(),
                phase = AlertPhase.RINGING,
                sources = listOf(req.source),
                channels = req.channels,
                startedAtMs = now,
                ringUntilMs = now + req.maxDurationMs,
                ringDurationMs = req.maxDurationMs,
                snoozeMs = req.snoozeMs,
                maxSnoozes = req.maxSnoozes,
                fullScreen = req.fullScreen,
            )
            Transition(s, "Alert started", RequestOutcome.STARTED)
        }
        is AlertState.Active -> {
            val sources = (state.sources + req.source).distinctBy { it.runId to it.title }
            val duration = maxOf(state.ringDurationMs, req.maxDurationMs)
            if (state.phase == AlertPhase.SNOOZED) {
                Transition(
                    state.copy(
                        phase = AlertPhase.RINGING,
                        sources = sources,
                        channels = state.channels + req.channels,
                        ringUntilMs = now + duration,
                        resumeAtMs = null,
                        ringDurationMs = duration,
                        fullScreen = state.fullScreen || req.fullScreen,
                    ),
                    "New event during snooze: ringing again",
                    RequestOutcome.RESUMED_FROM_SNOOZE,
                )
            } else {
                Transition(
                    state.copy(
                        sources = sources,
                        channels = state.channels + req.channels,
                        ringUntilMs = maxOf(state.ringUntilMs, now + req.maxDurationMs),
                        ringDurationMs = duration,
                        fullScreen = state.fullScreen || req.fullScreen,
                    ),
                    "Joined the alert that is already ringing",
                    RequestOutcome.JOINED,
                )
            }
        }
    }

    fun stop(state: AlertState, reason: String): Transition =
        Transition(AlertState.Idle, if (state is AlertState.Active) reason else "Nothing to stop")

    fun snooze(state: AlertState, now: Long): Transition = when {
        state !is AlertState.Active -> Transition(state, "Nothing to snooze")
        state.phase == AlertPhase.SNOOZED -> Transition(state, "Already snoozed")
        state.snoozesLeft == 0 -> Transition(AlertState.Idle, "No snoozes left: stopped")
        else -> Transition(
            state.copy(phase = AlertPhase.SNOOZED, resumeAtMs = now + state.snoozeMs, snoozeCount = state.snoozeCount + 1),
            "Snoozed (${state.snoozeCount + 1} of ${state.maxSnoozes})",
        )
    }

    /** Call when [nextDeadline] is reached (or on any wake-up, e.g. after process restart). */
    fun tick(state: AlertState, now: Long): Transition {
        if (state !is AlertState.Active) return Transition(state, "Idle")
        return when (state.phase) {
            AlertPhase.RINGING ->
                if (now >= state.ringUntilMs) Transition(AlertState.Idle, "Rang for the maximum time: stopped")
                else Transition(state, "Still ringing")
            AlertPhase.SNOOZED -> {
                val resumeAt = state.resumeAtMs ?: now
                if (now >= resumeAt) {
                    Transition(
                        state.copy(phase = AlertPhase.RINGING, resumeAtMs = null, ringUntilMs = now + state.ringDurationMs),
                        "Snooze over: ringing again",
                    )
                } else Transition(state, "Still snoozed")
            }
        }
    }
}

sealed interface AlertResult {
    /** [detail] explains what really happened, e.g. that the full-screen screen wasn't allowed. */
    data class Ok(val outcome: RequestOutcome, val detail: String) : AlertResult
    data class Failed(val reason: String) : AlertResult
}

/**
 * The one owner of alarm state (implemented on Android in :app). Actions talk to this instead of
 * playing sounds themselves, which is what guarantees "only one alarm at a time" and atomic stop.
 */
interface AlertController {
    val state: StateFlow<AlertState>
    suspend fun request(req: AlertRequest): AlertResult
    suspend fun stop(reason: String = "Stopped by you")
    suspend fun snooze()
}
