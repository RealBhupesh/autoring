package com.priorityring.blocks.notification

import android.app.Notification
import android.graphics.Typeface
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TextAppearanceSpan
import androidx.core.app.NotificationCompat
import com.priorityring.engine.notification.RawMessage
import com.priorityring.engine.notification.RawNotification
import com.priorityring.engine.notification.TextLine

/**
 * Reads an Android [StatusBarNotification] into the engine's plain [RawNotification].
 *
 * Notification extras are a loosely-typed Bundle filled however each app likes, and reading a
 * value can even throw (bad parcels from buggy apps). So every field is read separately and a
 * failure just leaves that field empty — one odd field never loses the whole notification.
 */
object NotificationExtractor {

    fun extract(sbn: StatusBarNotification, appLabel: String?): RawNotification {
        val n = sbn.notification
        val e: Bundle = n.extras ?: Bundle.EMPTY
        fun text(key: String): String? = safe { e.getCharSequence(key)?.toString() }

        // AndroidX understands both the platform and the compat MessagingStyle formats.
        val messaging = safe { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) }

        return RawNotification(
            packageName = sbn.packageName,
            key = sbn.key,
            postTimeMs = sbn.postTime,
            appLabel = appLabel,
            category = n.category,
            channelId = n.channelId,
            groupKey = sbn.groupKey,
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOngoing = sbn.isOngoing,
            title = text(Notification.EXTRA_TITLE_BIG) ?: text(Notification.EXTRA_TITLE),
            text = text(Notification.EXTRA_TEXT),
            bigText = text(Notification.EXTRA_BIG_TEXT),
            subText = text(Notification.EXTRA_SUB_TEXT),
            summaryText = text(Notification.EXTRA_SUMMARY_TEXT),
            infoText = text(Notification.EXTRA_INFO_TEXT),
            tickerText = safe { n.tickerText?.toString() },
            textLines = safe {
                e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { TextLine(it.toString(), leadingEmphasis(it)) }
            }.orEmpty(),
            messages = safe {
                messaging?.messages?.map { m ->
                    // A message with no person was sent by the phone's owner.
                    RawMessage(sender = m.person?.name?.toString(), text = m.text?.toString(), timeMs = m.timestamp)
                }
            }.orEmpty(),
            conversationTitle = safe { messaging?.conversationTitle?.toString() } ?: text(Notification.EXTRA_CONVERSATION_TITLE),
            isGroupConversation = safe { messaging?.isGroupConversation },
            selfName = safe { messaging?.user?.name?.toString() } ?: text(LEGACY_SELF_NAME),
            template = safe { e.getString(Notification.EXTRA_TEMPLATE) },
            actionTitles = safe { n.actions?.mapNotNull { it.title?.toString() } }.orEmpty(),
            hasReplyAction = safe { n.actions?.any { !it.remoteInputs.isNullOrEmpty() } } ?: false,
        )
    }

    /**
     * Gmail's summary lines look like "Rahul Sharma   Quarterly report" with the sender in bold.
     * The bold span is the only reliable separator, so we read it before the text is flattened.
     */
    private fun leadingEmphasis(cs: CharSequence): String? {
        if (cs !is Spanned) return null
        val bold = cs.getSpans(0, cs.length, StyleSpan::class.java)
            .filter { it.style == Typeface.BOLD || it.style == Typeface.BOLD_ITALIC }
        val appearance = cs.getSpans(0, cs.length, TextAppearanceSpan::class.java).toList()
        val span = (bold + appearance).firstOrNull { cs.getSpanStart(it) == 0 && cs.getSpanEnd(it) < cs.length }
            ?: return null
        return cs.subSequence(0, cs.getSpanEnd(span)).toString().trim().takeIf { it.isNotEmpty() }
    }

    /** Deprecated platform key, but older app versions still set it, so we keep reading it. */
    @Suppress("DEPRECATION")
    private val LEGACY_SELF_NAME = Notification.EXTRA_SELF_DISPLAY_NAME

    private inline fun <T> safe(block: () -> T): T? = try { block() } catch (_: Exception) { null }
}
