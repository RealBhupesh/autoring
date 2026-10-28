package com.priorityring.app.ui.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.priorityring.app.runtime.MonitorHealth
import com.priorityring.app.runtime.MonitorStatus
import com.priorityring.app.runtime.NotificationMonitorService
import com.priorityring.blocks.android.AndroidDeviceCapabilities
import com.priorityring.data.db.RunEntity
import com.priorityring.data.repo.MacroRepository
import com.priorityring.data.repo.RunRepository
import com.priorityring.data.repo.StoredMacro
import com.priorityring.data.settings.SettingsRepository
import com.priorityring.engine.alert.AlertChannels
import com.priorityring.engine.alert.AlertController
import com.priorityring.engine.alert.AlertRequest
import com.priorityring.engine.alert.AlertSource
import com.priorityring.engine.alert.AlertState
import com.priorityring.engine.alert.SoundSpec
import com.priorityring.engine.alert.VibrationPattern
import com.priorityring.engine.block.Requirement
import com.priorityring.engine.run.MacroEngine
import com.priorityring.engine.run.RunningMacro
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How to fix a broken state. */
sealed interface HealthFix {
    data class Open(val label: String, val intent: Intent) : HealthFix
    data object Rebind : HealthFix
}

/** Overall monitoring state shown at the top of Home. Never silent when something is wrong. */
sealed interface Health {
    data object Healthy : Health
    data object Connecting : Health
    data object Paused : Health
    data class Broken(val reason: String, val fix: HealthFix?) : Health
}

data class HomeUiState(
    val health: Health = Health.Connecting,
    val masterEnabled: Boolean = true,
    val enabledMacros: Int = 0,
    val totalMacros: Int = 0,
    val recentRuns: List<RunEntity> = emptyList(),
    val running: List<RunningMacro> = emptyList(),
    val alert: AlertState = AlertState.Idle,
    val lastEventAt: Long? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    macros: MacroRepository,
    runs: RunRepository,
    private val capabilities: AndroidDeviceCapabilities,
    private val health: MonitorHealth,
    private val engine: MacroEngine,
    private val alerts: AlertController,
) : ViewModel() {

    // Special-access grants can't be observed, so we re-check whenever the screen resumes
    // (e.g. coming back from the system settings screen).
    private val listenerGranted = MutableStateFlow(capabilities.isGranted(Requirement.NOTIFICATION_LISTENER))

    fun refresh() {
        listenerGranted.value = capabilities.isGranted(Requirement.NOTIFICATION_LISTENER)
    }

    private val base = combine(settings.settings, macros.macros, runs.recent(5), listenerGranted, health.status) { s, list, recent, granted, status ->
        HomeUiState(
            health = health(s.masterEnabled, granted, status),
            masterEnabled = s.masterEnabled,
            enabledMacros = list.count { it is StoredMacro.Ok && it.macro.enabled },
            totalMacros = list.size,
            recentRuns = recent,
            lastEventAt = status.lastEventAt,
        )
    }

    val state: StateFlow<HomeUiState> = combine(base, engine.running, alerts.state) { b, running, alert ->
        b.copy(running = running, alert = alert)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun health(masterEnabled: Boolean, granted: Boolean, s: MonitorStatus): Health {
        val now = System.currentTimeMillis()
        return when {
            !masterEnabled -> Health.Paused
            !granted -> Health.Broken(
                "Notification access is off, so notifications can't trigger macros.",
                capabilities.settingsIntent(Requirement.NOTIFICATION_LISTENER)?.let { HealthFix.Open("Grant access", it) },
            )
            s.listenerConnected == false -> Health.Broken(
                "Android disconnected PriorityRing from your notifications. Nothing will trigger until it reconnects.",
                HealthFix.Rebind,
            )
            s.listenerConnected == null && now - s.processStartedAt > 20_000 -> Health.Broken(
                "Notification access is on, but Android hasn't connected PriorityRing yet. Try turning access off and on.",
                capabilities.settingsIntent(Requirement.NOTIFICATION_LISTENER)?.let { HealthFix.Open("Open settings", it) },
            )
            s.foregroundError != null && now - (s.foregroundErrorAt ?: 0) < 6 * 3_600_000L -> Health.Broken(
                "Android blocked PriorityRing from running in the background (${s.foregroundError}). " +
                    "Alarms may be silent. Allow background activity / disable battery optimisation.",
                HealthFix.Open("App settings", appDetails()),
            )
            s.listenerConnected == null -> Health.Connecting
            else -> Health.Healthy
        }
    }

    private fun appDetails() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun rebind() {
        try {
            NotificationListenerService.requestRebind(ComponentName(context, NotificationMonitorService::class.java))
        } catch (_: Exception) {
            // Not granted: the health card will say so on next refresh.
        }
        refresh()
    }

    fun setMasterEnabled(value: Boolean) {
        viewModelScope.launch { settings.setMasterEnabled(value) }
    }

    /** Rings for 30 s exactly like a macro would, so you can check volume / DND / lock screen. */
    fun testAlarm() = viewModelScope.launch {
        alerts.request(
            AlertRequest(
                source = AlertSource("test-${System.currentTimeMillis()}", "test", "Test alarm", "Test alarm", "PriorityRing"),
                channels = AlertChannels(sound = SoundSpec(), vibration = VibrationPattern.ALARM),
                maxDurationMs = 30_000,
                maxSnoozes = 1,
                snoozeMs = 60_000,
            ),
        )
    }

    fun stopAlarm() = viewModelScope.launch { alerts.stop("Stopped on Home") }
    fun snoozeAlarm() = viewModelScope.launch { alerts.snooze() }
    fun stopRun(runId: String) = viewModelScope.launch { engine.stop(runId) }
    fun stopAll() = viewModelScope.launch {
        alerts.stop("Stopped with “Stop all”")
        engine.stopAll()
    }
}
