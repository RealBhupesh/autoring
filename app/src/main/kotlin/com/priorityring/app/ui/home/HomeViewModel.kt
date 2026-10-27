package com.priorityring.app.ui.home

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.priorityring.blocks.android.AndroidDeviceCapabilities
import com.priorityring.data.db.RunEntity
import com.priorityring.data.repo.MacroRepository
import com.priorityring.data.repo.RunRepository
import com.priorityring.data.repo.StoredMacro
import com.priorityring.data.settings.SettingsRepository
import com.priorityring.engine.block.Requirement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Overall monitoring state shown at the top of Home. */
sealed interface Health {
    data object Healthy : Health
    data object Paused : Health
    /** Something is wrong; [fix] opens the screen that solves it, when there is one. */
    data class Broken(val reason: String, val fixLabel: String?, val fix: Intent?) : Health
}

data class HomeUiState(
    val health: Health = Health.Healthy,
    val masterEnabled: Boolean = true,
    val enabledMacros: Int = 0,
    val totalMacros: Int = 0,
    val recentRuns: List<RunEntity> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settings: SettingsRepository,
    macros: MacroRepository,
    runs: RunRepository,
    private val capabilities: AndroidDeviceCapabilities,
) : ViewModel() {

    // Special-access grants can't be observed, so we re-check whenever the screen resumes
    // (e.g. coming back from the system settings screen).
    private val listenerGranted = MutableStateFlow(capabilities.isGranted(Requirement.NOTIFICATION_LISTENER))

    fun refresh() {
        listenerGranted.value = capabilities.isGranted(Requirement.NOTIFICATION_LISTENER)
    }

    val state: StateFlow<HomeUiState> = combine(
        settings.settings, macros.macros, runs.recent(5), listenerGranted,
    ) { s, list, recent, granted ->
        val enabled = list.count { it is StoredMacro.Ok && it.macro.enabled }
        val health = when {
            !s.masterEnabled -> Health.Paused
            !granted -> Health.Broken(
                reason = "Notification access is off, so notifications can't trigger macros.",
                fixLabel = "Grant access",
                fix = capabilities.settingsIntent(Requirement.NOTIFICATION_LISTENER),
            )
            else -> Health.Healthy
        }
        HomeUiState(health, s.masterEnabled, enabled, list.size, recent)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setMasterEnabled(value: Boolean) {
        viewModelScope.launch { settings.setMasterEnabled(value) }
    }
}
