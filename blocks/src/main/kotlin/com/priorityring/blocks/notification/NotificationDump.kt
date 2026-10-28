package com.priorityring.blocks.notification

import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification

/**
 * A text dump of a notification's raw extras, for the in-app inspector. With [redact] on (the
 * default) every word is reduced to its first letter plus dots, so the *shape* of the data can be
 * shared for debugging without sharing the content: "Rahul Sharma" → "R•••• S•••••".
 */
object NotificationDump {

    fun dump(sbn: StatusBarNotification, redact: Boolean = true): String = buildString {
        val n = sbn.notification
        appendLine("package: ${sbn.packageName}")
        appendLine("key: ${if (redact) redactKey(sbn.key) else sbn.key}")
        appendLine("postTime: ${sbn.postTime}  ongoing: ${sbn.isOngoing}  groupKey: ${sbn.groupKey?.let { if (redact) redactKey(it) else it }}")
        appendLine("category: ${n.category}  channel: ${n.channelId}  flags: 0x${Integer.toHexString(n.flags)}")
        n.actions?.forEachIndexed { i, a ->
            appendLine("action[$i]: ${a.title}  remoteInputs=${a.remoteInputs?.size ?: 0}")
        }
        appendLine("extras:")
        dumpBundle(this, n.extras ?: Bundle.EMPTY, "  ", redact, depth = 0)
    }

    @Suppress("DEPRECATION") // Bundle.get(String) is the only way to read values of unknown type.
    private fun dumpBundle(out: StringBuilder, b: Bundle, indent: String, redact: Boolean, depth: Int) {
        for (key in b.keySet().sorted()) {
            val v = try { b.get(key) } catch (e: Exception) { "<unreadable: ${e.javaClass.simpleName}>" }
            out.append(indent).append(key).append(" (").append(v?.javaClass?.simpleName ?: "null").append("): ")
            when {
                v is Bundle && depth < 3 -> { out.appendLine(); dumpBundle(out, v, "$indent  ", redact, depth + 1) }
                v is Array<*> -> {
                    out.appendLine("[${v.size}]")
                    v.forEachIndexed { i, item ->
                        out.append(indent).append("  [").append(i).append("] ")
                        if (item is Bundle && depth < 3) { out.appendLine(); dumpBundle(out, item, "$indent    ", redact, depth + 1) }
                        else out.appendLine(value(item, redact))
                    }
                }
                else -> out.appendLine(value(v, redact))
            }
        }
    }

    private fun value(v: Any?, redact: Boolean): String = when (v) {
        null -> "null"
        is CharSequence -> {
            val styled = if (v is android.text.Spanned && v.getSpans(0, v.length, Any::class.java).isNotEmpty()) " [styled]" else ""
            "\"" + (if (redact) redactText(v.toString()) else v.toString()) + "\"" + styled
        }
        is Number, is Boolean -> v.toString()
        is Parcelable -> "<${v.javaClass.simpleName}>"
        else -> "<${v.javaClass.simpleName}>"
    }

    fun redactText(s: String): String =
        s.replace(Regex("""\p{L}[\p{L}\p{N}]*|\p{N}+""")) { m -> m.value.first() + "•".repeat(m.value.length - 1) }

    /** Keys contain phone numbers / email ids; keep only their structure. */
    private fun redactKey(k: String) = k.split('|').joinToString("|") { if (it.length > 6) redactText(it) else it }
}
