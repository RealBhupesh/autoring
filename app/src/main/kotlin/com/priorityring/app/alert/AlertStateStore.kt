package com.priorityring.app.alert

import android.content.Context
import androidx.core.content.edit
import com.priorityring.engine.alert.AlertState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the alert state so a STOP tapped after Android killed and recreated the process still
 * finds (and clears) the alert. Written with commit() — synchronous — because the process may die
 * right after.
 */
@Singleton
class AlertStateStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("alert_state", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): AlertState = prefs.getString(KEY, null)
        ?.let { runCatching { json.decodeFromString(AlertState.serializer(), it) }.getOrNull() }
        ?: AlertState.Idle

    fun save(state: AlertState) {
        prefs.edit(commit = true) { putString(KEY, json.encodeToString(AlertState.serializer(), state)) }
    }

    private companion object {
        const val KEY = "state"
    }
}
