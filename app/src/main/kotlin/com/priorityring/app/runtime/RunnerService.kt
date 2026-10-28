package com.priorityring.app.runtime

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.priorityring.app.MainActivity
import com.priorityring.app.alert.AlertActionReceiver
import com.priorityring.blocks.android.NotificationChannels
import com.priorityring.engine.alert.AlertController
import com.priorityring.engine.alert.AlertState
import com.priorityring.engine.run.MacroEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the process alive while macros run or an alarm rings.
 *
 * Types (declared in the manifest, see PROGRESS.md D14):
 * - `mediaPlayback`: playing the alarm sound — the documented purpose of this type, and what
 *   Android 17 requires for background audio;
 * - `specialUse` (Android 14+): running user-built automations, which no other type describes.
 *
 * It also holds a partial wake lock, so waits and snooze timers fire on time with the screen off.
 */
@AndroidEntryPoint
class RunnerService : Service() {

    @Inject lateinit var engine: MacroEngine
    @Inject lateinit var alerts: AlertController
    @Inject lateinit var health: MonitorHealth
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    private var wakeLock: PowerManager.WakeLock? = null
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called promptly (within a few seconds) after startForegroundService().
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(emptyList(), false), foregroundTypes())
            health.foregroundOk()
        } catch (e: Exception) {
            health.foregroundFailed(e.message ?: e.javaClass.simpleName)
            stopSelf()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        if (observer == null) {
            observer = scope.launch {
                combine(engine.running, alerts.state) { runs, alert -> runs to alert }.collect { (runs, alert) ->
                    if (runs.isEmpty() && alert !is AlertState.Active) {
                        stopSelf()
                    } else {
                        getSystemService<android.app.NotificationManager>()
                            ?.notify(NOTIFICATION_ID, buildNotification(runs.map { it.macroName }, alert is AlertState.Active))
                    }
                }
            }
        }
        // If Android kills us, don't restart: running macros are not resumed (by design).
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observer?.cancel()
        observer = null
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun foregroundTypes(): Int = when {
        Build.VERSION.SDK_INT >= 34 ->
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        Build.VERSION.SDK_INT >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        else -> 0
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService<PowerManager>()
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PriorityRing:runner")
            ?.apply {
                setReferenceCounted(false)
                // Safety net: never hold the CPU awake longer than the longest possible run.
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
    }

    private fun buildNotification(running: List<String>, alertActive: Boolean): Notification {
        NotificationChannels.ensureCreated(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when {
            running.isNotEmpty() -> "Running: " + running.distinct().joinToString(", ")
            alertActive -> "Alarm active"
            else -> "Working…"
        }
        return NotificationCompat.Builder(this, NotificationChannels.RUNNING)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("PriorityRing")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Stop all", AlertActionReceiver.pendingIntent(this, AlertActionReceiver.ACTION_STOP_ALL))
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1000
        const val WAKE_LOCK_TIMEOUT_MS = 65 * 60_000L
    }
}
