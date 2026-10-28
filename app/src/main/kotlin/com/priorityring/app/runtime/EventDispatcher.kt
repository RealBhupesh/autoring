package com.priorityring.app.runtime

import com.priorityring.data.repo.MacroRepository
import com.priorityring.data.repo.StoredMacro
import com.priorityring.data.settings.SettingsRepository
import com.priorityring.engine.event.TriggerEvent
import com.priorityring.engine.notification.NotificationParsers
import com.priorityring.engine.notification.RawNotification
import com.priorityring.engine.run.DispatchResult
import com.priorityring.engine.run.MacroEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The bridge from Android events to the engine. System callbacks hand events in here; a single
 * consumer feeds them to the engine in arrival order (so a quick re-post can never overtake the
 * original and dodge dedupe).
 */
@Singleton
class EventDispatcher @Inject constructor(
    private val engine: MacroEngine,
    private val macros: MacroRepository,
    private val settings: SettingsRepository,
    private val health: MonitorHealth,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val queue = Channel<TriggerEvent>(Channel.UNLIMITED)

    init {
        scope.launch {
            // Cooldowns survive process restarts.
            macros.getAll().forEach { m -> m.lastRunAt?.let { engine.seedLastRun(m.id, it) } }
            for (event in queue) {
                runCatching { dispatch(event) }
            }
        }
    }

    /** Called from the notification listener (main thread); never blocks. */
    fun onNotification(raw: RawNotification) {
        health.eventReceived()
        queue.trySend(NotificationParsers.toEvent(raw))
    }

    /** Runs [event] now and returns what happened; used by the simulator and debug receiver. */
    suspend fun dispatch(event: TriggerEvent): DispatchResult {
        val enabled = macros.getAll().filterIsInstance<StoredMacro.Ok>().map { it.macro }
        val paused = !settings.settings.first().masterEnabled
        return engine.dispatch(event, enabled, paused)
    }
}
