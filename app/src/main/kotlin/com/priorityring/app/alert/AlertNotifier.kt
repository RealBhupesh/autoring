package com.priorityring.app.alert

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.priorityring.blocks.android.NotificationChannels
import com.priorityring.engine.alert.AlertPhase
import com.priorityring.engine.alert.AlertState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The alarm notification: heads-up with STOP / SNOOZE, and a full-screen intent to [AlarmActivity]
 * when Android allows it.
 *
 * Full-screen intents (Android 14+) are auto-granted only to calling and alarm-clock apps, and the
 * user can revoke them. Without the grant Android shows the notification as an expanded heads-up
 * instead — still with working STOP / SNOOZE buttons.
 */
@Singleton
class AlertNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    fun canUseFullScreen(): Boolean =
        if (Build.VERSION.SDK_INT >= 34) context.getSystemService<NotificationManager>()?.canUseFullScreenIntent() == true
        else true

    fun canPost(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Shows / updates / removes the notification to match [state]. */
    fun render(old: AlertState, state: AlertState) {
        val nm = NotificationManagerCompat.from(context)
        if (state !is AlertState.Active) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        NotificationChannels.ensureCreated(context)
        // Re-alert (pop up again) only when ringing starts; joins and snoozes update silently.
        val startsRinging = state.phase == AlertPhase.RINGING &&
            (old !is AlertState.Active || old.alertId != state.alertId || old.phase != AlertPhase.RINGING)
        if (startsRinging) nm.cancel(NOTIFICATION_ID)

        val latest = state.sources.last()
        val others = state.sources.size - 1
        val title = latest.title + if (others > 0) " (+$others more)" else ""
        val body = when (state.phase) {
            AlertPhase.RINGING -> listOfNotNull(latest.app, latest.macroName).joinToString(" · ") +
                (latest.preview?.let { "\n$it" } ?: "")
            AlertPhase.SNOOZED -> "Snoozed until " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.resumeAtMs ?: 0))
        }
        val screen = PendingIntent.getActivity(
            context, 1,
            Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(context, NotificationChannels.ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(!startsRinging)
            .setContentIntent(screen)
            .addAction(0, if (state.phase == AlertPhase.SNOOZED) "Cancel alarm" else "STOP", AlertActionReceiver.pendingIntent(context, AlertActionReceiver.ACTION_STOP))
        if (state.phase == AlertPhase.RINGING && state.snoozesLeft > 0) {
            b.addAction(0, "Snooze", AlertActionReceiver.pendingIntent(context, AlertActionReceiver.ACTION_SNOOZE))
        }
        if (state.phase == AlertPhase.RINGING && state.fullScreen && canUseFullScreen()) {
            b.setFullScreenIntent(screen, true)
        }
        try {
            nm.notify(NOTIFICATION_ID, b.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked: sound still plays; STOP is available in the app.
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}
