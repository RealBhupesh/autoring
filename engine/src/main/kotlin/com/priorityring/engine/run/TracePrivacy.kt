package com.priorityring.engine.run

/**
 * Applies the privacy setting to a trace before it is stored.
 *
 * Notification *content* (text, title, message bodies) is sensitive; who it was from and which app
 * isn't (that's the point of the history). With text storage off (the default), content variables
 * are removed and any place a condition quoted them in its reason is masked. With it on, they're
 * kept but truncated.
 */
object TracePrivacy {
    /** Variables considered message content. Everything else (sender, app, …) is kept. */
    val CONTENT_VARIABLES = setOf("text", "title", "big_text", "subject", "messages", "lines")

    const val MAX_STORED_CHARS = 200
    const val HIDDEN = "‹hidden›"
    private const val MIN_MASK_LENGTH = 3

    fun apply(trace: RunTrace, storeText: Boolean): RunTrace {
        if (storeText) {
            return trace.copy(variables = trace.variables.mapValues { (_, v) -> v?.let(::truncate) })
        }
        // A content value that is also a kept value (Gmail's title *is* the sender's name) isn't secret.
        val kept = trace.variables.filterKeys { it !in CONTENT_VARIABLES }.values.filterNotNull().toSet()
        val secrets = trace.variables
            .filterKeys { it in CONTENT_VARIABLES }
            .values.filterNotNull()
            .filter { it.length >= MIN_MASK_LENGTH && it !in kept }
            // Longest first, so a full value is masked before any shorter value it contains.
            .sortedByDescending { it.length }
        fun mask(s: String?): String? {
            if (s == null) return null
            var out = s
            for (secret in secrets) {
                out = out!!.replace(secret, HIDDEN)
                // Reasons often quote a shortened preview ("first 37 chars…"); mask those too.
                if (secret.length > 40) out = out.replace(secret.take(37), HIDDEN)
            }
            return out
        }
        return trace.copy(
            variables = trace.variables.filterKeys { it !in CONTENT_VARIABLES },
            reason = mask(trace.reason),
            steps = trace.steps.map { it.copy(label = mask(it.label)!!, detail = mask(it.detail)) },
        )
    }

    private fun truncate(s: String) = if (s.length <= MAX_STORED_CHARS) s else s.take(MAX_STORED_CHARS - 1) + "…"
}
