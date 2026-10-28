package com.priorityring.app.alert

import com.priorityring.app.runtime.ApplicationScope
import com.priorityring.app.runtime.MonitorHealth
import com.priorityring.blocks.hardware.Torch
import com.priorityring.blocks.hardware.Vibration
import com.priorityring.engine.alert.AlertChannels
import com.priorityring.engine.alert.AlertController
import com.priorityring.engine.alert.AlertMachine
import com.priorityring.engine.alert.AlertPhase
import com.priorityring.engine.alert.AlertRequest
import com.priorityring.engine.alert.AlertResult
import com.priorityring.engine.alert.AlertState
import com.priorityring.engine.alert.RequestOutcome
import com.priorityring.engine.alert.Transition
import com.priorityring.engine.alert.nextDeadline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single owner of alarm state. Every change goes through [AlertMachine] (pure, unit-tested),
 * then this class makes reality match: persists the state, starts/stops sound, vibration and torch,
 * updates the notification, and schedules the next timeout or snooze end.
 *
 * Because outputs are *reconciled* against the state (not toggled per event), stop is atomic: one
 * state change to Idle silences all three channels together.
 */
@Singleton
class AndroidAlertController @Inject constructor(
    private val store: AlertStateStore,
    private val notifier: AlertNotifier,
    private val sound: AlarmSound,
    private val vibration: Vibration,
    private val torch: Torch,
    private val health: MonitorHealth,
    @ApplicationScope private val scope: CoroutineScope,
) : AlertController {

    private val mutex = Mutex()
    private val _state = MutableStateFlow(store.load())
    override val state: StateFlow<AlertState> = _state.asStateFlow()

    /** What is physically on right now. */
    private var applied = AlertChannels()
    private var tickJob: Job? = null

    init {
        // After process death: re-evaluate the persisted state (it may have expired meanwhile) and
        // restore outputs / notification. If it's still ringing, ringing resumes.
        scope.launch {
            mutex.withLock { apply(AlertMachine.tick(_state.value, now()), restoring = true) }
        }
    }

    override suspend fun request(req: AlertRequest): AlertResult = mutex.withLock {
        val t = AlertMachine.request(_state.value, req, now()) { UUID.randomUUID().toString() }
        val soundNote = apply(t)
        val notes = mutableListOf(
            when (t.requestOutcome) {
                RequestOutcome.STARTED -> "Alarm started"
                RequestOutcome.JOINED -> "Joined the alarm already ringing"
                RequestOutcome.RESUMED_FROM_SNOOZE -> "Alarm was snoozed: ringing again now"
                null -> t.note
            },
        )
        if (req.fullScreen && req.channels.sound != null && !notifier.canUseFullScreen()) {
            notes += "full-screen not allowed, shown as a notification"
        }
        if (!notifier.canPost()) notes += "notifications are off, so STOP is only in the app"
        soundNote?.let { notes += it }
        health.status.value.foregroundError?.let { notes += "foreground service blocked: $it" }
        AlertResult.Ok(t.requestOutcome ?: RequestOutcome.JOINED, notes.joinToString("; "))
    }

    override suspend fun stop(reason: String) {
        mutex.withLock { apply(AlertMachine.stop(_state.value, reason)) }
    }

    override suspend fun snooze() {
        mutex.withLock { apply(AlertMachine.snooze(_state.value, now())) }
    }

    /** Must hold [mutex]. Returns a sound warning, if any. */
    private fun apply(t: Transition, restoring: Boolean = false): String? {
        val old = _state.value
        val new = t.state
        if (new != old || restoring) store.save(new)
        _state.value = new
        val soundNote = reconcile(new)
        if (new != old || restoring) notifier.render(if (restoring) AlertState.Idle else old, new)
        schedule(new)
        return soundNote
    }

    private fun reconcile(state: AlertState): String? {
        val wanted = if (state is AlertState.Active && state.phase == AlertPhase.RINGING) state.channels else AlertChannels()
        var note: String? = null
        if (wanted.sound != applied.sound) {
            sound.stop()
            wanted.sound?.let { note = sound.start(it) }
        }
        if (wanted.vibration != applied.vibration) {
            vibration.stop()
            wanted.vibration?.let { vibration.start(it.timings, repeat = true) }
        }
        if (wanted.torch != applied.torch) {
            torch.stop()
            wanted.torch?.let { if (!torch.start(scope, it)) note = listOfNotNull(note, "torch unavailable (camera in use?)").joinToString("; ") }
        }
        applied = wanted
        return note
    }

    private fun schedule(state: AlertState) {
        tickJob?.cancel()
        val deadline = state.nextDeadline() ?: return
        tickJob = scope.launch {
            delay((deadline - now()).coerceAtLeast(0))
            mutex.withLock { apply(AlertMachine.tick(_state.value, now())) }
        }
    }

    private fun now() = System.currentTimeMillis()
}
