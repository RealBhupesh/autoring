package com.priorityring.blocks.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * Notification channels (Android 8+). Each channel's importance is fixed once created and then
 * belongs to the user, who can change it in system settings, so ids must never be reused for a
 * different purpose. Creating an existing channel again is a harmless no-op.
 */
object NotificationChannels {
    /** The alarm: heads-up, over the lock screen. Silent at channel level because we play the sound ourselves. */
    const val ALARM = "alarm"

    /** Ongoing "PriorityRing is running a macro" notification required for the foreground service. */
    const val RUNNING = "running"

    /** "Show notification" action, by importance. */
    const val MACRO_HIGH = "macro_high"
    const val MACRO_DEFAULT = "macro_default"
    const val MACRO_LOW = "macro_low"

    /** Health warnings (e.g. "monitoring stopped"). */
    const val HEALTH = "health"

    fun ensureCreated(context: Context) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(ALARM, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alarms rung by your macros, with Stop and Snooze."
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true) // only takes effect if the user grants DND access
                },
                NotificationChannel(RUNNING, "Running macros", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while a macro is running, so Android keeps it alive."
                    setShowBadge(false)
                },
                NotificationChannel(MACRO_HIGH, "Macro notifications (urgent)", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(MACRO_DEFAULT, "Macro notifications", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(MACRO_LOW, "Macro notifications (quiet)", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(HEALTH, "Monitoring health", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Warns you when PriorityRing has stopped receiving notifications."
                },
            ),
        )
    }
}
