package com.priorityring.engine

import com.priorityring.engine.alert.AlertChannels
import com.priorityring.engine.alert.AlertMachine
import com.priorityring.engine.alert.AlertPhase
import com.priorityring.engine.alert.AlertRequest
import com.priorityring.engine.alert.AlertSource
import com.priorityring.engine.alert.AlertState
import com.priorityring.engine.alert.RequestOutcome
import com.priorityring.engine.alert.SoundSpec
import com.priorityring.engine.alert.TorchPattern
import com.priorityring.engine.alert.VibrationPattern
import com.priorityring.engine.alert.nextDeadline
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AlertMachineTest {
    private fun req(run: String, sound: Boolean = true, torch: Boolean = false, max: Long = 120_000, snoozes: Int = 2) = AlertRequest(
        source = AlertSource(run, "m-$run", "Macro $run", "Sender $run", "Gmail"),
        channels = AlertChannels(sound = if (sound) SoundSpec() else null, vibration = VibrationPattern.ALARM, torch = if (torch) TorchPattern.BLINK else null),
        maxDurationMs = max, snoozeMs = 300_000, maxSnoozes = snoozes,
    )

    private var ids = 0
    private fun newId() = "a${++ids}"

    @Test
    fun `first request starts ringing`() {
        val t = AlertMachine.request(AlertState.Idle, req("1"), now = 1_000, newId = ::newId)
        val s = assertIs<AlertState.Active>(t.state)
        assertEquals(RequestOutcome.STARTED, t.requestOutcome)
        assertEquals(AlertPhase.RINGING, s.phase)
        assertEquals(121_000, s.ringUntilMs)
        assertEquals(121_000, s.nextDeadline())
    }

    @Test
    fun `second request joins instead of starting new audio`() {
        val s1 = AlertMachine.request(AlertState.Idle, req("1"), 0, ::newId).state as AlertState.Active
        val t = AlertMachine.request(s1, req("2", sound = false, torch = true, max = 300_000), 10_000, ::newId)
        val s2 = assertIs<AlertState.Active>(t.state)
        assertEquals(RequestOutcome.JOINED, t.requestOutcome)
        assertEquals(s1.alertId, s2.alertId)                 // same alert
        assertEquals(s1.channels.sound, s2.channels.sound)   // sound unchanged (not restarted)
        assertEquals(TorchPattern.BLINK, s2.channels.torch)  // torch added
        assertEquals(listOf("1", "2"), s2.sources.map { it.runId })
        assertEquals(310_000, s2.ringUntilMs)                // extended to the later deadline
    }

    @Test
    fun `stop is immediate from any state`() {
        val ringing = AlertMachine.request(AlertState.Idle, req("1"), 0, ::newId).state
        assertEquals(AlertState.Idle, AlertMachine.stop(ringing, "Stopped").state)
        val snoozed = AlertMachine.snooze(ringing, 1_000).state
        assertEquals(AlertState.Idle, AlertMachine.stop(snoozed, "Stopped").state)
    }

    @Test
    fun `snooze silences, then rings again after the snooze length`() {
        val ringing = AlertMachine.request(AlertState.Idle, req("1"), 0, ::newId).state
        val snoozed = assertIs<AlertState.Active>(AlertMachine.snooze(ringing, 10_000).state)
        assertEquals(AlertPhase.SNOOZED, snoozed.phase)
        assertEquals(310_000, snoozed.resumeAtMs)
        assertEquals(1, snoozed.snoozeCount)
        assertEquals(310_000, snoozed.nextDeadline())

        assertEquals(snoozed, AlertMachine.tick(snoozed, 309_999).state)
        val again = assertIs<AlertState.Active>(AlertMachine.tick(snoozed, 310_000).state)
        assertEquals(AlertPhase.RINGING, again.phase)
        assertEquals(430_000, again.ringUntilMs)
    }

    @Test
    fun `max snooze count then stop`() {
        var s: AlertState = AlertMachine.request(AlertState.Idle, req("1", snoozes = 2), 0, ::newId).state
        var now = 0L
        repeat(2) {
            s = AlertMachine.snooze(s, now).state
            now += 300_000
            s = AlertMachine.tick(s, now).state
            assertEquals(AlertPhase.RINGING, (s as AlertState.Active).phase)
        }
        assertEquals(0, (s as AlertState.Active).snoozesLeft)
        val t = AlertMachine.snooze(s, now)
        assertEquals(AlertState.Idle, t.state)
        assertEquals("No snoozes left: stopped", t.note)
    }

    @Test
    fun `new event during snooze rings immediately`() {
        val ringing = AlertMachine.request(AlertState.Idle, req("1"), 0, ::newId).state
        val snoozed = AlertMachine.snooze(ringing, 1_000).state
        val t = AlertMachine.request(snoozed, req("2"), 2_000, ::newId)
        val s = assertIs<AlertState.Active>(t.state)
        assertEquals(RequestOutcome.RESUMED_FROM_SNOOZE, t.requestOutcome)
        assertEquals(AlertPhase.RINGING, s.phase)
        assertNull(s.resumeAtMs)
        assertEquals(1, s.snoozeCount) // snooze count isn't reset by a new event
        assertEquals(122_000, s.ringUntilMs)
    }

    @Test
    fun `ringing times out at its max duration`() {
        val ringing = AlertMachine.request(AlertState.Idle, req("1"), 0, ::newId).state
        assertEquals(ringing, AlertMachine.tick(ringing, 119_999).state)
        assertEquals(AlertState.Idle, AlertMachine.tick(ringing, 120_000).state)
    }

    @Test
    fun `idle ignores snooze and tick`() {
        assertEquals(AlertState.Idle, AlertMachine.snooze(AlertState.Idle, 0).state)
        assertEquals(AlertState.Idle, AlertMachine.tick(AlertState.Idle, 0).state)
        assertNull(AlertState.Idle.nextDeadline())
    }

    @Test
    fun `state survives a JSON round trip (process death)`() {
        val s = AlertMachine.snooze(AlertMachine.request(AlertState.Idle, req("1", torch = true), 0, ::newId).state, 5).state
        val json = Json.encodeToString(AlertState.serializer(), s)
        assertEquals(s, Json.decodeFromString(AlertState.serializer(), json))
        assertEquals(AlertState.Idle, Json.decodeFromString(AlertState.serializer(), Json.encodeToString(AlertState.serializer(), AlertState.Idle)))
    }
}
