package com.priorityring.engine

import com.priorityring.engine.blocks.core.NotificationPostedTrigger
import com.priorityring.engine.notification.GenericParser
import com.priorityring.engine.notification.GmailParser
import com.priorityring.engine.notification.KnownApps
import com.priorityring.engine.notification.NotificationParsers
import com.priorityring.engine.notification.RawMessage
import com.priorityring.engine.notification.RawNotification
import com.priorityring.engine.notification.TextLine
import com.priorityring.engine.notification.WhatsAppParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fixtures mirror real extras (field-for-field what NotificationExtractor reads). When the phone's
 * notification inspector shows a new shape, add it here as a fixture.
 */
class NotificationParsersTest {

    // ---- Gmail ----------------------------------------------------------------------------------

    private val gmailSingle = RawNotification(
        packageName = KnownApps.GMAIL, key = "0|com.google.android.gm|12|gig:123|10123", postTimeMs = 1,
        appLabel = "Gmail", category = "email", groupKey = "gmail-me@gmail.com",
        title = "Rahul Sharma", text = "Quarterly report",
        bigText = "Quarterly report\nHi team, please find the Q3 numbers attached.",
        subText = "me@gmail.com", template = "android.app.Notification\$BigTextStyle",
        actionTitles = listOf("Archive", "Reply"), hasReplyAction = false,
    )

    @Test
    fun `gmail single email`() {
        val p = NotificationParsers.parse(gmailSingle)
        assertEquals("gmail", p.parser)
        assertEquals("Rahul Sharma", p.sender)
        assertEquals("Quarterly report", p.subject)
        assertTrue(p.text!!.startsWith("Quarterly report\nHi team"))
        assertEquals("me@gmail.com", p.account)
        assertEquals("Gmail", p.app)
        assertFalse(p.isSummary)
    }

    @Test
    fun `gmail grouped summary with bold senders and a plain line`() {
        val summary = gmailSingle.copy(
            key = "0|com.google.android.gm|0|gig:summary|10123", isGroupSummary = true,
            title = "3 new messages", text = "Rahul Sharma, Priya, Amazon.in", bigText = null,
            template = "android.app.Notification\$InboxStyle",
            textLines = listOf(
                TextLine("Rahul Sharma   Quarterly report", emphasis = "Rahul Sharma"),
                TextLine("Priya   Lunch tomorrow?", emphasis = "Priya"),
                TextLine("Amazon.in   Your order has shipped"), // no span info
            ),
        )
        val p = NotificationParsers.parse(summary)
        assertTrue(p.isSummary)
        assertEquals("Rahul Sharma", p.sender)
        assertEquals(listOf("Rahul Sharma", "Priya", "Amazon.in"), p.senders)
        assertEquals(3, p.messageCount)
        assertEquals("Rahul Sharma, Priya, Amazon.in", p.variables()["senders"])
    }

    @Test
    fun `gmail with almost nothing does not crash`() {
        val p = GmailParser.parse(RawNotification(KnownApps.GMAIL, "k", 0))
        assertNull(p.sender)
        assertNull(p.text)
        assertEquals("Gmail", p.app)
        assertTrue(p.notes.any { "No sender" in it })
    }

    // ---- WhatsApp -------------------------------------------------------------------------------

    @Test
    fun `whatsapp one to one messaging style uses the latest message`() {
        val raw = RawNotification(
            packageName = KnownApps.WHATSAPP, key = "0|com.whatsapp|1|91987@s.whatsapp.net|10140", postTimeMs = 1,
            appLabel = "WhatsApp", category = "msg", title = "Rahul", text = "urgent: call me",
            template = "android.app.Notification\$MessagingStyle", selfName = "You", isGroupConversation = false,
            messages = listOf(RawMessage("Rahul", "hey", 100), RawMessage("Rahul", "urgent: call me", 200)),
            hasReplyAction = true,
        )
        val p = NotificationParsers.parse(raw)
        assertEquals("whatsapp", p.parser)
        assertEquals("Rahul", p.sender)
        assertEquals("urgent: call me", p.text)
        assertEquals("Rahul", p.conversation)
        assertFalse(p.isGroup)
        assertEquals(2, p.messageCount)
    }

    @Test
    fun `whatsapp group messaging style strips message count from the group name`() {
        val raw = RawNotification(
            packageName = KnownApps.WHATSAPP, key = "k", postTimeMs = 1, title = "Family: Mom",
            conversationTitle = "Family (3 messages)", isGroupConversation = true,
            messages = listOf(RawMessage("Dad", "dinner?", 1), RawMessage("Mom", "URGENT come home", 2)),
        )
        val p = WhatsAppParser.parse(raw)
        assertEquals("Mom", p.sender)
        assertEquals("Family", p.conversation)
        assertTrue(p.isGroup)
        assertEquals(listOf("Dad", "Mom"), p.senders)
    }

    @Test
    fun `whatsapp message sent by me has no sender, uses self name`() {
        val raw = RawNotification(
            packageName = KnownApps.WHATSAPP, key = "k", postTimeMs = 1, selfName = "Bhupesh",
            messages = listOf(RawMessage("Rahul", "hi", 1), RawMessage(null, "on my way", 2)),
        )
        assertEquals("Bhupesh", WhatsAppParser.parse(raw).sender)
    }

    @Test
    fun `whatsapp fallback group text uses the name prefix`() {
        val raw = RawNotification(KnownApps.WHATSAPP, "k", 1, title = "Office", text = "Rahul: urgent: server is down")
        val p = WhatsAppParser.parse(raw)
        assertEquals("Rahul", p.sender)
        assertEquals("urgent: server is down", p.text)
        assertEquals("Office", p.conversation)
        assertTrue(p.isGroup)
    }

    @Test
    fun `whatsapp fallback one to one`() {
        val p = WhatsAppParser.parse(RawNotification(KnownApps.WHATSAPP, "k", 1, title = "Rahul", text = "see you at 5"))
        assertEquals("Rahul", p.sender)
        assertEquals("see you at 5", p.text)
        assertFalse(p.isGroup)
    }

    @Test
    fun `whatsapp summary has no sender`() {
        val raw = RawNotification(KnownApps.WHATSAPP, "k", 1, isGroupSummary = true, title = "WhatsApp", text = "5 messages from 3 chats")
        val p = WhatsAppParser.parse(raw)
        assertTrue(p.isSummary)
        assertNull(p.sender)
        assertEquals(5, p.messageCount)
    }

    @Test
    fun `whatsapp business uses the whatsapp parser`() {
        assertEquals("whatsapp", NotificationParsers.parse(RawNotification(KnownApps.WHATSAPP_BUSINESS, "k", 1, title = "A", text = "b")).parser)
    }

    // ---- generic --------------------------------------------------------------------------------

    @Test
    fun `generic bank sms style`() {
        val raw = RawNotification("com.bank.app", "k", 1, title = "HDFC Bank", text = "INR 500.00 debited from a/c XX1234")
        val p = NotificationParsers.parse(raw)
        assertEquals("generic", p.parser)
        assertEquals("HDFC Bank", p.sender)
        assertEquals("INR 500.00 debited from a/c XX1234", p.text)
        assertEquals("com.bank.app", p.app) // no label known → package name
    }

    @Test
    fun `generic prefers big text then lines`() {
        assertEquals("long", GenericParser.parse(RawNotification("p", "k", 1, text = "short", bigText = "long")).text)
        assertEquals("b", GenericParser.parse(RawNotification("p", "k", 1, textLines = listOf(TextLine("a"), TextLine("b")))).text)
        assertEquals("tick", GenericParser.parse(RawNotification("p", "k", 1, tickerText = "tick")).text)
    }

    @Test
    fun `blank fields count as missing`() {
        val p = GenericParser.parse(RawNotification("p", "k", 1, title = "   ", text = ""))
        assertNull(p.sender)
        assertNull(p.text)
    }

    // ---- events & dedupe ------------------------------------------------------------------------

    @Test
    fun `dedupe key ignores post time but not content`() {
        val a = NotificationParsers.toEvent(gmailSingle)
        val sameContentLater = NotificationParsers.toEvent(gmailSingle.copy(postTimeMs = 999_999))
        val newContent = NotificationParsers.toEvent(gmailSingle.copy(text = "Another subject"))
        assertEquals(a.dedupeKey, sameContentLater.dedupeKey)
        assertNotEquals(a.dedupeKey, newContent.dedupeKey)
    }

    @Test
    fun `new whatsapp message in the same conversation is not a duplicate`() {
        val base = RawNotification(KnownApps.WHATSAPP, "same-key", 1, messages = listOf(RawMessage("A", "one", 1)))
        val next = base.copy(messages = base.messages + RawMessage("A", "two", 2))
        assertNotEquals(NotificationParsers.toEvent(base).dedupeKey, NotificationParsers.toEvent(next).dedupeKey)
    }

    @Test
    fun `trigger excludes summaries and ongoing by default`() {
        val t = NotificationPostedTrigger
        val summaryEvent = NotificationParsers.toEvent(RawNotification(KnownApps.WHATSAPP, "k", 1, isGroupSummary = true, title = "WhatsApp", text = "2 messages"))
        assertFalse(t.match(config(t), summaryEvent).matched)
        assertTrue(t.match(config(t, "include_summaries" to true), summaryEvent).matched)
        val ongoing = NotificationParsers.toEvent(RawNotification("music", "k", 1, isOngoing = true, title = "Song"))
        assertFalse(t.match(config(t), ongoing).matched)
        assertTrue(t.match(config(t, "include_ongoing" to true), ongoing).matched)
    }

    @Test
    fun `trigger summary text`() {
        val t = NotificationPostedTrigger
        assertEquals("Notification from any app", t.summarize(config(t)))
        assertEquals("Notification from Gmail or WhatsApp", t.summarize(config(t, "apps" to listOf(KnownApps.GMAIL, KnownApps.WHATSAPP))))
        assertEquals("Notification from Example", t.summarize(config(t, "apps" to listOf("com.example"))))
    }
}
