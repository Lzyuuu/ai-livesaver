package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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
        val vision = ProviderProtocol.visionRequest("vision-model", "data:image/jpeg;base64,abc")
        val visionMessages = vision.getJSONArray("messages")
        val visionContent = visionMessages.getJSONObject(0).getJSONArray("content")
        assertEquals(1, visionMessages.length())
        assertEquals(2, visionContent.length())
        assertEquals(
            "data:image/jpeg;base64,abc",
            visionContent.getJSONObject(1).getJSONObject("image_url").getString("url"),
        )
        assertFalse(vision.toString().contains("memory", ignoreCase = true))
    }

    @Test
    fun includesRecapAndMemoriesInCharacterContext() {
        val prompt = buildChatSystemPrompt(
            ResidentCharacter(1, "Mira", "A patient old friend."),
            listOf(LongTermMemory(1, 1, "Mira", "User likes tea.", 4, 5, true)),
            ConversationRecap("They planned a quiet weekend.", 12, 13, false),
            listOf(WorldFact(1, "The old station is closed.", true, 1)),
            listOf(CharacterCognition(1, 1, "Mira suspects it may reopen.", false, 1)),
            MemberWorldContext("user", "", "Asia/Shanghai"),
            MemberWorldContext("character:1", "Paris", "Europe/Paris"),
        )

        assertTrue(prompt.contains("Conversation recap through message #12"))
        assertTrue(prompt.contains("They planned a quiet weekend."))
        assertTrue(prompt.contains("User likes tea."))
        assertTrue(prompt.contains("The old station is closed."))
        assertTrue(prompt.contains("Mira suspects it may reopen."))
        assertTrue(prompt.contains("User-disclosed time zone: Asia/Shanghai"))
        assertTrue(prompt.contains("Character location: Paris"))

        val messages = (1L..15L).map { ChatMessage(it, 1, "user", "m$it", it) }
        val recent = recentMessagesForContext(
            messages,
            ConversationRecap("Earlier events", 12, 13, false),
        )
        assertEquals(listOf(13L, 14L, 15L), recent.map(ChatMessage::id))
    }

    @Test
    fun retriesOnlyTransientProviderFailures() {
        assertTrue(isTransientProviderFailure("HTTP 429"))
        assertTrue(isTransientProviderFailure("HTTP 503"))
        assertTrue(isTransientProviderFailure("connection reset"))
        assertFalse(isTransientProviderFailure("HTTP 401"))
        assertFalse(isTransientProviderFailure("HTTP 404"))
    }
}
