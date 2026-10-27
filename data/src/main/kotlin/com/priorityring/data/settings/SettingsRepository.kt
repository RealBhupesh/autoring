package com.priorityring.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** App-wide settings. Small key/value data lives in DataStore, not Room. */
data class AppSettings(
    /** Master switch: when false nothing runs, but events are still logged as "skipped: paused". */
    val masterEnabled: Boolean = true,
    val onboardingComplete: Boolean = false,
    /** How long run history is kept: 1, 7 or 30 days. */
    val retentionDays: Int = 7,
    /** Privacy: notification text is stored in traces only if the user opts in. */
    val storeNotificationText: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
) {
    companion object {
        val RETENTION_CHOICES = listOf(1, 7, 30)
    }
}

@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    private object Keys {
        val MASTER = booleanPreferencesKey("master_enabled")
        val ONBOARDED = booleanPreferencesKey("onboarding_complete")
        val RETENTION = intPreferencesKey("retention_days")
        val STORE_TEXT = booleanPreferencesKey("store_notification_text")
        val THEME = stringPreferencesKey("theme_mode")
    }

    val settings: Flow<AppSettings> = store.data
        .map { p ->
            val defaults = AppSettings()
            AppSettings(
                masterEnabled = p[Keys.MASTER] ?: defaults.masterEnabled,
                onboardingComplete = p[Keys.ONBOARDED] ?: defaults.onboardingComplete,
                retentionDays = p[Keys.RETENTION]?.takeIf { it in AppSettings.RETENTION_CHOICES }
                    ?: defaults.retentionDays,
                storeNotificationText = p[Keys.STORE_TEXT] ?: defaults.storeNotificationText,
                themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: defaults.themeMode,
            )
        }
        .distinctUntilChanged()

    suspend fun setMasterEnabled(value: Boolean) = store.edit { it[Keys.MASTER] = value }
    suspend fun setOnboardingComplete(value: Boolean) = store.edit { it[Keys.ONBOARDED] = value }
    suspend fun setStoreNotificationText(value: Boolean) = store.edit { it[Keys.STORE_TEXT] = value }
    suspend fun setThemeMode(value: ThemeMode) = store.edit { it[Keys.THEME] = value.name }

    suspend fun setRetentionDays(days: Int) {
        require(days in AppSettings.RETENTION_CHOICES) { "Unsupported retention $days" }
        store.edit { it[Keys.RETENTION] = days }
    }
}
