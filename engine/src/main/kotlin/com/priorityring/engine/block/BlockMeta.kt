package com.priorityring.engine.block

import com.priorityring.engine.variables.VariableSpec

enum class BlockKind { TRIGGER, CONDITION, ACTION }

/**
 * How much access Android gives a normal app for this block. Shown in the UI and in BLOCKS.md.
 */
enum class Tier(val number: Int, val label: String) {
    /** Works for any app, possibly after a user-granted permission. */
    STANDARD(1, "Tier 1"),

    /** Needs one-time elevated setup (Shizuku or an adb grant). Planned add-on, not v1. */
    ELEVATED(2, "Tier 2"),

    /** Impossible for third-party apps without root. Listed in LIMITATIONS.md, never faked. */
    ROOT_ONLY(3, "Tier 3"),
}

/**
 * Library grouping. A plain data class rather than an enum so a new block can introduce a new
 * category without touching the engine.
 */
data class BlockCategory(val id: String, val label: String, val order: Int) {
    companion object {
        val NOTIFICATIONS = BlockCategory("notifications", "Notifications", 10)
        val TIME = BlockCategory("time", "Date & time", 20)
        val DEVICE = BlockCategory("device", "Device & battery", 30)
        val CONNECTIVITY = BlockCategory("connectivity", "Connectivity", 40)
        val TEXT = BlockCategory("text", "Text & variables", 50)
        val ALERT = BlockCategory("alert", "Alert", 60)
        val SOUND_DISPLAY = BlockCategory("sound_display", "Sound & display", 70)
        val APPS_WEB = BlockCategory("apps_web", "Apps & web", 80)
        val MEDIA = BlockCategory("media", "Media", 90)
        val MESSAGING = BlockCategory("messaging", "Messaging", 100)
        val CONTROL = BlockCategory("control", "Control", 110)
        val MANUAL = BlockCategory("manual", "Manual", 120)
    }
}

/**
 * Something the user must grant before a block works, e.g. notification access.
 *
 * The id is a stable string (not an enum) so new requirements can be added by new blocks; the
 * Android layer knows how to check each id and which settings screen fixes it.
 */
data class Requirement(val id: String, val label: String, val why: String) {
    companion object {
        val NOTIFICATION_LISTENER = Requirement(
            "notification_listener", "Notification access",
            "Lets PriorityRing read notifications from other apps so they can trigger macros.",
        )
        val POST_NOTIFICATIONS = Requirement(
            "post_notifications", "Show notifications",
            "Needed to show alerts and the alarm notification.",
        )
        val FULL_SCREEN_INTENT = Requirement(
            "full_screen_intent", "Full-screen alerts",
            "Lets the alarm screen appear over the lock screen.",
        )
        val DND_ACCESS = Requirement(
            "dnd_access", "Do Not Disturb access",
            "Needed to read or change Do Not Disturb and ringer mode.",
        )
        val WRITE_SETTINGS = Requirement(
            "write_settings", "Modify system settings",
            "Needed to change screen brightness.",
        )
        val USAGE_ACCESS = Requirement(
            "usage_access", "Usage access",
            "Lets PriorityRing see which app is in the foreground, for the \"app launched\" trigger.",
        )
        val EXACT_ALARMS = Requirement(
            "exact_alarms", "Alarms & reminders",
            "Lets time-based triggers fire at the exact minute.",
        )
    }
}

/**
 * Everything the app needs to know about a block *without* running it: how to list it in the
 * library, render its config form, describe its outputs and check whether it can work here.
 */
data class BlockMeta(
    /** Stable, namespaced, never renamed (it's stored in users' macros), e.g. "action.vibrate". */
    val id: String,
    val kind: BlockKind,
    val name: String,
    val description: String,
    val category: BlockCategory,
    /** Icon key resolved by the UI layer (keeps the engine free of Android drawables). */
    val icon: String,
    val tier: Tier = Tier.STANDARD,
    val fields: List<ConfigField> = emptyList(),
    /** Variables this trigger provides to the rest of the macro. Triggers only. */
    val outputs: List<VariableSpec> = emptyList(),
    val requirements: List<Requirement> = emptyList(),
    /** Inclusive Android API level bounds, null = no bound. */
    val minSdk: Int? = null,
    val maxSdk: Int? = null,
    /** Non-null when using this block blocks or complicates Play Store publishing. */
    val playStoreNote: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Block id must not be blank" }
        val dupes = fields.groupBy { it.key }.filterValues { it.size > 1 }.keys
        require(dupes.isEmpty()) { "Block $id has duplicate field keys: $dupes" }
    }
}
