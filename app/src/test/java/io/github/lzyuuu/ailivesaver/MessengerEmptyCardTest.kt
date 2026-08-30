package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MS-02 空态大头像卡判据（参考 ref-11-chat-detail）：
 * 仅空会话且无进行中发送/首条流式回复时展示。
 */
class MessengerEmptyCardTest {
    @Test
    fun showsOnlyForEmptyIdleConversation() {
        assertTrue(shouldShowMessengerEmptyCard(messageCount = 0, sending = false))
    }

    @Test
    fun hiddenWhenConversationHasMessages() {
        assertFalse(shouldShowMessengerEmptyCard(messageCount = 1, sending = false))
        assertFalse(shouldShowMessengerEmptyCard(messageCount = 20, sending = false))
    }

    @Test
    fun hiddenWhileFirstReplyIsSendingOrStreaming() {
        assertFalse(shouldShowMessengerEmptyCard(messageCount = 0, sending = true))
        assertFalse(shouldShowMessengerEmptyCard(messageCount = 1, sending = true))
    }

    @Test
    fun personaSummaryExpandsCharacterCardMacros() {
        assertEquals(
            "Root looks out for Yu.",
            displayPersonaSummary("{{char}} looks out for {{user}}.", "Root", "Yu"),
        )
        // 用户名为空时回落到「你」，没有宏时原样返回。
        assertEquals(
            "Root looks out for 你.",
            displayPersonaSummary("{{char}} looks out for {{user}}.", "Root", ""),
        )
        assertEquals("quiet poet", displayPersonaSummary("quiet poet", "Root", "Yu"))
    }
}
