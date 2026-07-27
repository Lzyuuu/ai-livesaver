package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
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
}
