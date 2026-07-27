package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderProtocolTest {
    @Test
    fun buildsEndpointAndParsesHeaders() {
        assertEquals(
            "https://example.com/v1/chat/completions",
            ProviderProtocol.chatCompletionsUrl(" https://example.com/v1/ "),
        )
        assertEquals(
            mapOf("X-Title" to "AI: Livesaver", "X-Empty" to ""),
            ProviderProtocol.parseHeaders(
                """
                X-Title: AI: Livesaver
                invalid
                X-Empty:
                """.trimIndent(),
            ),
        )
        assertEquals(
            "hello",
            ProviderProtocol.parseReply(
                """{"choices":[{"message":{"content":" hello "}}]}""",
            ),
        )
    }

    @Test
    fun includesRecapAndMemoriesInCharacterContext() {
        val prompt = buildChatSystemPrompt(
            ResidentCharacter(1, "Mira", "A patient old friend."),
            listOf(LongTermMemory(1, 1, "Mira", "User likes tea.", 4, 5, true)),
            ConversationRecap("They planned a quiet weekend.", 12, 13, false),
        )

        assertTrue(prompt.contains("Conversation recap through message #12"))
        assertTrue(prompt.contains("They planned a quiet weekend."))
        assertTrue(prompt.contains("User likes tea."))

        val messages = (1L..15L).map { ChatMessage(it, 1, "user", "m$it", it) }
        val recent = recentMessagesForContext(
            messages,
            ConversationRecap("Earlier events", 12, 13, false),
        )
        assertEquals(listOf(13L, 14L, 15L), recent.map(ChatMessage::id))
    }
}
