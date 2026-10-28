package com.priorityring.app.runtime

import android.content.ComponentName
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.priorityring.blocks.notification.NotificationDump
import com.priorityring.blocks.notification.NotificationExtractor
import com.priorityring.engine.notification.NotificationParsers
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Android binds this service once the user grants Notification access, and calls us for every
 * notification posted by any app. Being bound by the system also keeps our process alive (as long
 * as the OS — or vivo's battery manager — lets it).
 *
 * Callbacks arrive on the main thread, so we only extract data here and hand off to the
 * dispatcher's background queue.
 */
@AndroidEntryPoint
class NotificationMonitorService : NotificationListenerService() {

    @Inject lateinit var dispatcher: EventDispatcher
    @Inject lateinit var health: MonitorHealth
    @Inject lateinit var recent: RecentNotifications

    private val labels = HashMap<String, String?>()
    private var nextId = 0L

    override fun onListenerConnected() {
        health.listenerConnected()
    }

    override fun onListenerDisconnected() {
        health.listenerDisconnected()
        // The only call allowed after a disconnect: ask Android to bind us again.
        try {
            requestRebind(ComponentName(this, NotificationMonitorService::class.java))
        } catch (_: Exception) {
            // Throws if access was revoked; the Home screen shows how to fix it.
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Our own notifications (alarms, "show notification") never trigger macros: loop prevention.
        if (sbn.packageName == packageName) return
        val raw = try {
            NotificationExtractor.extract(sbn, label(sbn.packageName))
        } catch (_: Exception) {
            return
        }
        recent.add(
            CapturedNotification(
                id = nextId++,
                atMs = System.currentTimeMillis(),
                packageName = sbn.packageName,
                app = raw.appLabel ?: sbn.packageName,
                parsed = NotificationParsers.parse(raw),
                redactedDump = runCatching { NotificationDump.dump(sbn, redact = true) }.getOrDefault("<dump failed>"),
                fullDump = runCatching { NotificationDump.dump(sbn, redact = false) }.getOrDefault("<dump failed>"),
            ),
        )
        dispatcher.onNotification(raw)
    }

    private fun label(pkg: String): String? = labels.getOrPut(pkg) {
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null // not visible to us (Android 11+ package visibility) — parsers fall back to known names
        }
    }
}
