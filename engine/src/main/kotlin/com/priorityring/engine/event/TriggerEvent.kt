package com.priorityring.engine.event

/**
 * A normalised "something happened" from any source: a notification, the charger, a timer, a tap
 * on the Quick Settings tile... Android-specific code converts raw system callbacks into these;
 * the engine only ever sees this shape.
 *
 * @param type namespaced event type, matched against [com.priorityring.engine.block.TriggerBlock.eventTypes].
 * @param timestampMs when it happened (epoch millis).
 * @param variables values exposed to the macro as `{name}`. Values may be null when the source
 *   didn't provide them (very common for notifications) — consumers must cope.
 * @param dedupeKey events with the same key are considered the same occurrence and fire at most once.
 *   Null means "never dedupe" (e.g. manual runs).
 * @param origin optional: id of the macro that caused this event, used for loop prevention.
 */
data class TriggerEvent(
    val type: String,
    val timestampMs: Long,
    val variables: Map<String, String?> = emptyMap(),
    val dedupeKey: String? = null,
    val origin: String? = null,
) {
    operator fun get(name: String): String? = variables[name]

    companion object {
        const val NOTIFICATION_POSTED = "notification.posted"
        const val NOTIFICATION_REMOVED = "notification.removed"
        const val MANUAL = "manual"
    }
}
