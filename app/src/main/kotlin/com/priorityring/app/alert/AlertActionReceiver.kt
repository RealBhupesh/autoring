package com.priorityring.app.alert

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.priorityring.app.runtime.ApplicationScope
import com.priorityring.engine.alert.AlertController
import com.priorityring.engine.run.MacroEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles STOP / SNOOZE / STOP ALL from notification buttons. Works even if Android killed the
 * process: the tap starts it again, the controller reloads the persisted state, and clears it.
 */
@AndroidEntryPoint
class AlertActionReceiver : BroadcastReceiver() {

    @Inject lateinit var alerts: AlertController
    @Inject lateinit var engine: MacroEngine
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        // goAsync keeps the process alive until our coroutine finishes (receivers normally get ~10 s).
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_STOP -> alerts.stop("Stopped from the notification")
                    ACTION_SNOOZE -> alerts.snooze()
                    ACTION_STOP_ALL -> {
                        alerts.stop("Stopped with “Stop all”")
                        engine.stopAll()
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.priorityring.action.STOP"
        const val ACTION_SNOOZE = "com.priorityring.action.SNOOZE"
        const val ACTION_STOP_ALL = "com.priorityring.action.STOP_ALL"

        fun pendingIntent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, AlertActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
