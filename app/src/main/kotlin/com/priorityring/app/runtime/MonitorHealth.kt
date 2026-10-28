package com.priorityring.app.runtime

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class MonitorStatus(
    /** null = not connected yet since this process started; false = Android disconnected us. */
    val listenerConnected: Boolean? = null,
    val lastConnectedAt: Long? = null,
    val lastDisconnectedAt: Long? = null,
    val lastEventAt: Long? = null,
    val processStartedAt: Long = System.currentTimeMillis(),
    /** Last failure to start the foreground service (Android blocked a background start). */
    val foregroundError: String? = null,
    val foregroundErrorAt: Long? = null,
)

/**
 * Live view of "is monitoring actually working". Times are also persisted so the diagnostics screen
 * can show when the last notification arrived even after the process was restarted.
 */
@Singleton
class MonitorHealth @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("monitor_health", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(
        MonitorStatus(
            lastConnectedAt = prefs.getLong(K_CONNECTED, 0).takeIf { it > 0 },
            lastDisconnectedAt = prefs.getLong(K_DISCONNECTED, 0).takeIf { it > 0 },
            lastEventAt = prefs.getLong(K_EVENT, 0).takeIf { it > 0 },
        ),
    )
    val status: StateFlow<MonitorStatus> = _status.asStateFlow()

    fun listenerConnected() = now().let { t ->
        _status.update { it.copy(listenerConnected = true, lastConnectedAt = t) }
        prefs.edit { putLong(K_CONNECTED, t) }
    }

    fun listenerDisconnected() = now().let { t ->
        _status.update { it.copy(listenerConnected = false, lastDisconnectedAt = t) }
        prefs.edit { putLong(K_DISCONNECTED, t) }
    }

    fun eventReceived() = now().let { t ->
        _status.update { it.copy(lastEventAt = t) }
        prefs.edit { putLong(K_EVENT, t) }
    }

    fun foregroundFailed(reason: String) = _status.update { it.copy(foregroundError = reason, foregroundErrorAt = now()) }
    fun foregroundOk() = _status.update { it.copy(foregroundError = null, foregroundErrorAt = null) }

    private fun now() = System.currentTimeMillis()

    private companion object {
        const val K_CONNECTED = "last_connected"
        const val K_DISCONNECTED = "last_disconnected"
        const val K_EVENT = "last_event"
    }
}
