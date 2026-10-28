package com.priorityring.engine.blocks.core

import com.priorityring.engine.block.BlockCategory
import com.priorityring.engine.block.BlockConfig
import com.priorityring.engine.block.BlockKind
import com.priorityring.engine.block.BlockMeta
import com.priorityring.engine.block.ConfigField
import com.priorityring.engine.block.Requirement
import com.priorityring.engine.block.TriggerBlock
import com.priorityring.engine.block.TriggerMatch
import com.priorityring.engine.event.TriggerEvent
import com.priorityring.engine.notification.KnownApps
import com.priorityring.engine.variables.VariableSpec

/**
 * Fires when any (or a chosen) app posts a notification. The Android listener does the reading and
 * parsing (Gmail / WhatsApp / generic presets are chosen automatically by app); this block only
 * decides whether a parsed notification counts. Filtering by sender or text is done with
 * conditions, so the same logic works for every trigger.
 */
object NotificationPostedTrigger : TriggerBlock {
    const val ID = "trigger.notification_posted"

    override val eventTypes = setOf(TriggerEvent.NOTIFICATION_POSTED)

    override val meta = BlockMeta(
        id = ID,
        kind = BlockKind.TRIGGER,
        name = "Notification received",
        description = "When an app posts a notification. Gmail and WhatsApp are parsed specially to find the real sender.",
        category = BlockCategory.NOTIFICATIONS,
        icon = "notification",
        fields = listOf(
            ConfigField.AppPicker(
                "apps", "From apps",
                help = "Leave empty for any app. An app can only trigger this if it actually shows a notification.",
            ),
            ConfigField.Toggle(
                "include_summaries", "Include group summaries",
                help = "Summaries like “3 new messages” usually repeat notifications you already got one by one.",
            ),
            ConfigField.Toggle("include_ongoing", "Include ongoing notifications", help = "Music players, downloads, navigation…"),
        ),
        outputs = listOf(
            VariableSpec("sender", "Sender (display name)", "Rahul Sharma"),
            VariableSpec("text", "Message text", "Can you call me?"),
            VariableSpec("title", "Notification title", "Rahul Sharma"),
            VariableSpec("subject", "Email subject (Gmail)", "Quarterly report"),
            VariableSpec("conversation", "Chat or group name", "Family"),
            VariableSpec("app", "App name", "Gmail"),
            VariableSpec("package", "App package", KnownApps.GMAIL),
            VariableSpec("account", "Account (Gmail)", "me@gmail.com"),
            VariableSpec("is_group", "Is a group chat (true/false)", "false"),
            VariableSpec("senders", "All senders in a summary", "Rahul, Priya"),
            VariableSpec("message_count", "Number of messages", "1"),
        ),
        requirements = listOf(Requirement.NOTIFICATION_LISTENER),
    )

    override fun summarize(config: BlockConfig): String {
        val apps = config.stringList("apps")
        return when {
            apps.isEmpty() -> "Notification from any app"
            apps.size <= 3 -> "Notification from " + apps.joinToString(" or ") { KnownApps.display(it) }
            else -> "Notification from ${apps.size} apps"
        }
    }

    override fun match(config: BlockConfig, event: TriggerEvent): TriggerMatch {
        val pkg = event["package"] ?: return TriggerMatch.no("Event has no app package")
        val app = event["app"] ?: pkg
        val apps = config.stringList("apps")
        if (apps.isNotEmpty() && pkg !in apps) return TriggerMatch.no("$app isn't one of the chosen apps")
        if (event["is_summary"] == "true" && !config.boolean("include_summaries")) {
            return TriggerMatch.no("Group summary from $app (summaries are excluded)")
        }
        if (event["is_ongoing"] == "true" && !config.boolean("include_ongoing")) {
            return TriggerMatch.no("Ongoing notification from $app (excluded)")
        }
        val who = event["sender"]?.let { " from $it" }.orEmpty()
        return TriggerMatch.yes("$app notification$who")
    }
}
