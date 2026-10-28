package com.priorityring.app.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.priorityring.app.runtime.MonitorHealth
import com.priorityring.blocks.android.AndroidDeviceCapabilities
import com.priorityring.data.repo.MacroRepository
import com.priorityring.data.repo.RunRepository
import com.priorityring.data.settings.AppSettings
import com.priorityring.data.settings.SettingsRepository
import com.priorityring.data.settings.ThemeMode
import com.priorityring.engine.block.Requirement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RequirementStatus(val requirement: Requirement, val granted: Boolean, val fix: Intent?)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val macros: MacroRepository,
    private val runs: RunRepository,
    private val capabilities: AndroidDeviceCapabilities,
    health: MonitorHealth,
) : ViewModel() {

    val monitor = health.status

    val appSettings = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _requirements = MutableStateFlow(check())
    val requirements = _requirements.asStateFlow()

    /** Called on resume: grants can change while we're in the system settings app. */
    fun refresh() {
        _requirements.value = check()
    }

    private fun check() = AndroidDeviceCapabilities.ALL.map {
        RequirementStatus(it, capabilities.isGranted(it), capabilities.settingsIntent(it))
    }

    fun setRetention(days: Int) = viewModelScope.launch { settings.setRetentionDays(days) }
    fun setStoreText(value: Boolean) = viewModelScope.launch { settings.setStoreNotificationText(value) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun deleteLogs() = viewModelScope.launch { runs.deleteAll() }

    fun deleteEverything() = viewModelScope.launch {
        runs.deleteAll()
        macros.deleteAll()
    }
}
