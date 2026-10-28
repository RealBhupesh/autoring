package com.priorityring.engine.notification

import com.priorityring.engine.event.TriggerEvent

/**
 * Notification → a normalised set of macro variables.
 *
 * All values are nullable; [notes] records how each was derived, so a trace can explain *why*
 * `{sender}` was, say, the group name instead of a person.
 */
data class ParsedNotification(
    val parser: String,
    val app: String,
    val packageName: String,
    val sender: String? = null,
    val title: String? = null,
    val text: String? = null,
    val subject: String? = null,
    val conversation: String? = null,
    val isGroup: Boolean = false,
    val isSummary: Boolean = false,
    val isOngoing: Boolean = false,
    val account: String? = null,
    val messageCount: Int? = null,
    /** All distinct senders found (summaries can contain several). */
    val senders: List<String> = emptyList(),
    val category: String? = null,
    val notes: List<String> = emptyList(),
) {
    fun variables(): Map<String, String?> = mapOf(
        "app" to app,
        "package" to packageName,
        "sender" to sender,
        "senders" to senders.takeIf { it.isNotEmpty() }?.joinToString(", "),
        "title" to title,
        "text" to text,
        "subject" to subject,
        "conversation" to conversation,
        "is_group" to isGroup.toString(),
        "is_summary" to isSummary.toString(),
        "is_ongoing" to isOngoing.toString(),
        "account" to account,
        "message_count" to messageCount?.toString(),
        "category" to category,
        "parser" to parser,
    )
}

interface NotificationParser {
    val id: String
    fun accepts(packageName: String): Boolean
    fun parse(raw: RawNotification): ParsedNotification
}

object NotificationParsers {
    val all: List<NotificationParser> = listOf(GmailParser, WhatsAppParser, GenericParser)

    fun parse(raw: RawNotification): ParsedNotification =
        all.first { it.accepts(raw.packageName) }.parse(raw)

    /**
     * Builds the engine event. The dedupe key combines Android's notification key with a hash of
     * the *content*, so a silent re-post of the same notification is a duplicate, but a new
     * message in the same conversation (same key, new content) is not.
     */
    fun toEvent(raw: RawNotification, parsed: ParsedNotification = parse(raw), simulated: Boolean = false): TriggerEvent =
        TriggerEvent(
            type = TriggerEvent.NOTIFICATION_POSTED,
            timestampMs = raw.postTimeMs,
            variables = parsed.variables(),
            dedupeKey = "${raw.key}|${contentHash(raw)}",
            simulated = simulated,
        )

    fun contentHash(raw: RawNotification): String {
        val last = raw.messages.lastOrNull()
        val parts = listOf(
            raw.packageName, raw.title, raw.text, raw.bigText, raw.subText,
            last?.sender, last?.text, last?.timeMs?.toString(), raw.messages.size.toString(),
            raw.textLines.joinToString("\n") { it.text },
        )
        // 64-bit FNV-1a: stable across runs and JVMs (unlike String.hashCode collisions on short inputs).
        var h = -0x340d631b7bdddcdbL
        for (p in parts) {
            for (c in (p ?: "\u0000")) { h = h xor c.code.toLong(); h *= 0x100000001b3L }
            h = h xor 0x1f; h *= 0x100000001b3L
        }
        return java.lang.Long.toHexString(h)
    }
}

/** Shared helpers. Blank strings are treated as missing everywhere. */
internal fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun appName(raw: RawNotification, fallback: String) =
    raw.appLabel.clean() ?: KnownApps.label(raw.packageName) ?: fallback

/** Gmail: title = sender display name, text = subject, big text = subject + snippet, sub text = account. */
object GmailParser : NotificationParser {
    override val id = "gmail"
    override fun accepts(packageName: String) = packageName == KnownApps.GMAIL

    override fun parse(raw: RawNotification): ParsedNotification {
        val app = appName(raw, "Gmail")
        val account = raw.subText.clean() ?: raw.summaryText.clean()
        if (raw.isGroupSummary || raw.textLines.isNotEmpty()) {
            // Summary like "3 new messages": InboxStyle lines "Sender   Subject" with the sender in bold.
            val lines = raw.textLines.filter { it.text.isNotBlank() }
            val senders = lines.mapNotNull { line -> line.emphasis.clean() ?: splitOnGap(line.text)?.first }.distinct()
            val notes = mutableListOf("Gmail group summary with ${lines.size} line(s)")
            if (lines.isNotEmpty() && senders.isEmpty()) notes += "Couldn't identify senders in summary lines"
            return ParsedNotification(
                parser = id, app = app, packageName = raw.packageName,
                sender = senders.firstOrNull(),
                senders = senders,
                title = raw.title.clean(),
                text = lines.joinToString("\n") { it.text }.clean() ?: raw.text.clean(),
                account = account,
                isSummary = raw.isGroupSummary,
                messageCount = lines.size.takeIf { it > 0 },
                category = raw.category,
                isOngoing = raw.isOngoing,
                notes = notes,
            )
        }
        val subject = raw.text.clean() ?: raw.bigText.clean()?.lineSequence()?.firstOrNull().clean()
        val sender = raw.title.clean()
        return ParsedNotification(
            parser = id, app = app, packageName = raw.packageName,
            sender = sender,
            senders = listOfNotNull(sender),
            title = sender,
            subject = subject,
            text = raw.bigText.clean() ?: subject,
            account = account,
            messageCount = 1,
            category = raw.category,
            isOngoing = raw.isOngoing,
            notes = listOf(if (sender != null) "Sender from title (display name, not a verified address)" else "No sender in notification"),
        )
    }

    /** "Rahul Sharma   Subject" → ("Rahul Sharma", "Subject"), splitting on a run of 2+ spaces. */
    private fun splitOnGap(s: String): Pair<String, String>? {
        val m = Regex("""^(.+?)\s{2,}(.+)$""").find(s.trim()) ?: return null
        return m.groupValues[1].trim() to m.groupValues[2].trim()
    }
}

/**
 * WhatsApp (and WhatsApp Business). Modern versions use MessagingStyle: the last message has the
 * real sender, the conversation title is the group name. Older/fallback formats put
 * "Sender: message" in the text for groups.
 */
object WhatsAppParser : NotificationParser {
    override val id = "whatsapp"
    override fun accepts(packageName: String) = packageName == KnownApps.WHATSAPP || packageName == KnownApps.WHATSAPP_BUSINESS

    private val countSuffix = Regex("""\s*\((\d+)\s+(new\s+)?messages?\)\s*$""", RegexOption.IGNORE_CASE)

    override fun parse(raw: RawNotification): ParsedNotification {
        val app = appName(raw, "WhatsApp")
        if (raw.isGroupSummary && raw.messages.isEmpty()) {
            // e.g. title "WhatsApp", text "5 messages from 3 chats": no single sender exists.
            return ParsedNotification(
                parser = id, app = app, packageName = raw.packageName,
                title = raw.title.clean(), text = raw.text.clean(), isSummary = true,
                messageCount = raw.text?.let { Regex("""(\d+)\s+(new\s+)?messages?""").find(it)?.groupValues?.get(1)?.toIntOrNull() },
                category = raw.category, isOngoing = raw.isOngoing,
                notes = listOf("WhatsApp summary: no individual sender"),
            )
        }
        if (raw.messages.isNotEmpty()) {
            val last = raw.messages.last()
            val conversation = raw.conversationTitle.clean()?.replace(countSuffix, "")
            val isGroup = raw.isGroupConversation ?: (conversation != null)
            val sender = last.sender.clean() ?: (raw.selfName.clean() ?: "You")
            val senders = raw.messages.mapNotNull { it.sender.clean() }.distinct()
            return ParsedNotification(
                parser = id, app = app, packageName = raw.packageName,
                sender = sender,
                senders = senders,
                title = raw.title.clean(),
                text = last.text.clean(),
                conversation = conversation ?: if (!isGroup) sender else null,
                isGroup = isGroup,
                isSummary = raw.isGroupSummary,
                messageCount = raw.messages.size,
                category = raw.category, isOngoing = raw.isOngoing,
                notes = listOf(
                    "MessagingStyle: ${raw.messages.size} message(s), using the latest",
                    if (last.sender == null) "Latest message was sent by you" else "Sender from message",
                ),
            )
        }
        // Fallback: plain title/text.
        val title = raw.title.clean()?.replace(countSuffix, "")
        val text = raw.bigText.clean() ?: raw.text.clean()
        val prefixed = text?.let { Regex("""^([^:\n]{1,60}):\s+(.+)$""", RegexOption.DOT_MATCHES_ALL).find(it) }
        return if (prefixed != null && prefixed.groupValues[1].trim() != title) {
            // Group style: title = group name, text = "Rahul: message".
            val sender = prefixed.groupValues[1].trim()
            ParsedNotification(
                parser = id, app = app, packageName = raw.packageName,
                sender = sender, senders = listOf(sender), title = title,
                text = prefixed.groupValues[2].trim(), conversation = title, isGroup = true,
                messageCount = 1, category = raw.category, isOngoing = raw.isOngoing,
                notes = listOf("Group message: sender taken from the \"Name: text\" prefix"),
            )
        } else {
            ParsedNotification(
                parser = id, app = app, packageName = raw.packageName,
                sender = title, senders = listOfNotNull(title), title = title, text = text,
                conversation = title, isGroup = false, messageCount = 1,
                category = raw.category, isOngoing = raw.isOngoing,
                notes = listOf("No MessagingStyle: sender taken from the title"),
            )
        }
    }
}

/** Any other app. Prefers MessagingStyle when present, then title / big text / text / lines. */
object GenericParser : NotificationParser {
    override val id = "generic"
    override fun accepts(packageName: String) = true

    override fun parse(raw: RawNotification): ParsedNotification {
        val app = appName(raw, raw.packageName)
        if (raw.messages.isNotEmpty()) {
            val last = raw.messages.last()
            val conversation = raw.conversationTitle.clean()
            return ParsedNotification(
                parser = id, app = app, packageName = raw.packageName,
                sender = last.sender.clean() ?: raw.selfName.clean() ?: "You",
                senders = raw.messages.mapNotNull { it.sender.clean() }.distinct(),
                title = raw.title.clean(), text = last.text.clean(),
                conversation = conversation, isGroup = raw.isGroupConversation ?: (conversation != null),
                isSummary = raw.isGroupSummary, messageCount = raw.messages.size,
                category = raw.category, isOngoing = raw.isOngoing,
                notes = listOf("MessagingStyle: using the latest of ${raw.messages.size} message(s)"),
            )
        }
        val lines = raw.textLines.map { it.text }.filter { it.isNotBlank() }
        val text = raw.bigText.clean() ?: raw.text.clean() ?: lines.lastOrNull().clean() ?: raw.tickerText.clean()
        return ParsedNotification(
            parser = id, app = app, packageName = raw.packageName,
            sender = raw.title.clean(), senders = listOfNotNull(raw.title.clean()),
            title = raw.title.clean(), text = text,
            account = raw.subText.clean(),
            isSummary = raw.isGroupSummary,
            messageCount = lines.size.takeIf { it > 1 },
            category = raw.category, isOngoing = raw.isOngoing,
            notes = listOf("Generic: sender = title (may be an app name, not a person)"),
        )
    }
}

/** Friendly names for common packages, so summaries read "Gmail" rather than a package id. */
object KnownApps {
    const val GMAIL = "com.google.android.gm"
    const val WHATSAPP = "com.whatsapp"
    const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    private val names = mapOf(
        GMAIL to "Gmail",
        WHATSAPP to "WhatsApp",
        WHATSAPP_BUSINESS to "WhatsApp Business",
        "com.google.android.apps.messaging" to "Messages",
        "org.telegram.messenger" to "Telegram",
        "com.Slack" to "Slack",
        "com.microsoft.office.outlook" to "Outlook",
        "com.google.android.calendar" to "Google Calendar",
        "com.android.shell" to "Shell (adb)",
    )

    fun label(pkg: String): String? = names[pkg]

    /** Best-effort readable name for summaries when no label is known. */
    fun display(pkg: String): String = names[pkg] ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
