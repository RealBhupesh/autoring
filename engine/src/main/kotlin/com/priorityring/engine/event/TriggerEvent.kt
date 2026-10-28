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
 * @param originChain ids of the macros whose actions (directly or indirectly) caused this event,
 *   oldest first. Used for loop prevention: a macro never runs from an event it caused itself.
 * @param simulated true for events from the simulator / test runs; shown as such in traces.
 */
data class TriggerEvent(
    val type: String,
    val timestampMs: Long,
    val variables: Map<String, String?> = emptyMap(),
    val dedupeKey: String? = null,
    val originChain: List<String> = emptyList(),
    val simulated: Boolean = false,
) {
    operator fun get(name: String): String? = variables[name]

    companion object {
        const val NOTIFICATION_POSTED = "notification.posted"
        const val NOTIFICATION_REMOVED = "notification.removed"
        const val MANUAL = "manual"
    }
}
