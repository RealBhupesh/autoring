package com.priorityring.engine.notification

import kotlinx.serialization.Serializable

/**
 * Everything we read from an Android notification, already flattened to plain Kotlin types by the
 * Android layer. **Every field may be missing**: apps fill notifications in very different ways,
 * and the same app changes its format between versions.
 *
 * Keeping this pure lets the parsers be unit-tested with realistic fixtures on the JVM.
 */
@Serializable
data class RawNotification(
    val packageName: String,
    /** Android's unique key for this notification (stable across updates of the same one). */
    val key: String,
    val postTimeMs: Long,
    val appLabel: String? = null,
    val category: String? = null,
    val channelId: String? = null,
    val groupKey: String? = null,
    val isGroupSummary: Boolean = false,
    val isOngoing: Boolean = false,
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val subText: String? = null,
    val summaryText: String? = null,
    val infoText: String? = null,
    val tickerText: String? = null,
    /** InboxStyle lines (EXTRA_TEXT_LINES); grouped summaries often list one message per line. */
    val textLines: List<TextLine> = emptyList(),
    /** MessagingStyle messages (EXTRA_MESSAGES), oldest first. */
    val messages: List<RawMessage> = emptyList(),
    val conversationTitle: String? = null,
    val isGroupConversation: Boolean? = null,
    val selfName: String? = null,
    /** e.g. "android.app.Notification$MessagingStyle". */
    val template: String? = null,
    val actionTitles: List<String> = emptyList(),
    val hasReplyAction: Boolean = false,
)

/**
 * A text line. [emphasis] is the leading bold part when the app styles it (Gmail bolds the sender
 * in "Rahul Sharma   Quarterly report"), which is the only reliable way to split sender and text.
 */
@Serializable
data class TextLine(val text: String, val emphasis: String? = null)

/** [sender] null means the message was sent by the phone's owner. */
@Serializable
data class RawMessage(val sender: String?, val text: String?, val timeMs: Long? = null)
