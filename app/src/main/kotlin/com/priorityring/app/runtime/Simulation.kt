package com.priorityring.app.runtime

import com.priorityring.engine.notification.KnownApps
import com.priorityring.engine.notification.NotificationParsers
import com.priorityring.engine.notification.RawMessage
import com.priorityring.engine.notification.RawNotification
import com.priorityring.engine.event.TriggerEvent
import java.util.UUID

/**
 * Builds fake notifications shaped exactly like the real apps' ones, so the simulator and the
 * adb test command exercise the same parsers and engine as real notifications.
 */
object Simulation {
    enum class Preset(val label: String, val packageName: String) {
        GMAIL("Gmail email", KnownApps.GMAIL),
        WHATSAPP("WhatsApp chat", KnownApps.WHATSAPP),
        WHATSAPP_GROUP("WhatsApp group", KnownApps.WHATSAPP),
        CUSTOM("Other app", "com.example.app"),
    }

    /**
     * @param key notification key; reuse the same key and content to test duplicate handling.
     */
    fun build(
        preset: Preset,
        sender: String,
        text: String,
        group: String = "",
        packageName: String = preset.packageName,
        key: String = "sim|" + UUID.randomUUID(),
    ): RawNotification {
        val now = System.currentTimeMillis()
        return when (preset) {
            Preset.GMAIL -> RawNotification(
                packageName = packageName, key = key, postTimeMs = now, appLabel = "Gmail", category = "email",
                title = sender, text = text.lineSequence().first(), bigText = text, subText = "me@gmail.com",
            )
            Preset.WHATSAPP -> RawNotification(
                packageName = packageName, key = key, postTimeMs = now, appLabel = "WhatsApp", category = "msg",
                title = sender, text = text, isGroupConversation = false,
                messages = listOf(RawMessage(sender, text, now)),
            )
            Preset.WHATSAPP_GROUP -> RawNotification(
                packageName = packageName, key = key, postTimeMs = now, appLabel = "WhatsApp", category = "msg",
                title = "${group.ifBlank { "Group" }}: $sender", text = text,
                conversationTitle = group.ifBlank { "Group" }, isGroupConversation = true,
                messages = listOf(RawMessage(sender, text, now)),
            )
            Preset.CUSTOM -> RawNotification(
                packageName = packageName, key = key, postTimeMs = now, appLabel = KnownApps.label(packageName),
                title = sender, text = text,
            )
        }
    }

    fun event(raw: RawNotification): TriggerEvent = NotificationParsers.toEvent(raw, simulated = true)
}
