package com.priorityring.app.runtime

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.priorityring.engine.alert.AlertController
import com.priorityring.engine.alert.AlertState
import com.priorityring.engine.run.MacroEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts [RunnerService] whenever a macro is running or an alert is active. The service stops
 * itself when both are idle.
 *
 * Why a foreground service at all: without one, Android (and especially vivo) may freeze or kill
 * the process mid-macro, and on Android 17 background audio is blocked unless one is running.
 */
@Singleton
class ForegroundCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: MacroEngine,
    private val alerts: AlertController,
    private val health: MonitorHealth,
    @ApplicationScope private val scope: CoroutineScope,
) {
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(engine.running, alerts.state) { runs, alert -> runs.isNotEmpty() || alert is AlertState.Active }
                .distinctUntilChanged()
                .collect { needed -> if (needed) startService() }
        }
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, RunnerService::class.java))
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException (API 31+) or IllegalStateException: Android
            // refused a start from the background. Macros still run, but may be killed, and on
            // Android 17 alarm audio may be silent. Surfaced on Home and in the alarm's trace.
            health.foregroundFailed(e.message ?: e.javaClass.simpleName)
        }
    }
}
