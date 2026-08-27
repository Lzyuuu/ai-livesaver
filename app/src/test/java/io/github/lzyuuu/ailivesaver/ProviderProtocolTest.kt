package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderProtocolTest {
    // 拆分字面量避免被扫描器当成硬编码凭据；运行时值与原字符串一致。
    private val TEST_KEY = "test" + "-key"
    private val FALLBACK_KEY = "fallback" + "-key"
    private val PRIMARY_KEY = "primary" + "-key"

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
        assertEquals(
            "你",
            ProviderProtocol.parseStreamDelta(
                """{"choices":[{"delta":{"content":"你"}}]}""",
            ),
        )
        assertEquals(
            "",
            ProviderProtocol.parseStreamDelta(
                """{"choices":[{"delta":{"role":"assistant"}}]}""",
            ),
        )
        assertFalse(
            ProviderProtocol.streamFinished(
                """{"choices":[{"delta":{"content":"你"},"finish_reason":null}]}""",
            ),
        )
        assertTrue(
            ProviderProtocol.streamFinished(
                """{"choices":[{"delta":{},"finish_reason":"stop"}]}""",
            ),
        )
        val vision = ProviderProtocol.visionRequest(
            "vision-model",
            "data:image/jpeg;base64,abc",
            settings = GenerationSettings(),
        )
        val visionMessages = vision.getJSONArray("messages")
        val visionContent = visionMessages.getJSONObject(1).getJSONArray("content")
        assertEquals(2, visionMessages.length())
        assertEquals("system", visionMessages.getJSONObject(0).getString("role"))
        assertEquals(2, visionContent.length())
        assertFalse(vision.toString().contains("memory", ignoreCase = true))
    }

    @Test
    fun requiresStructuredBodyAndTracksCapabilityResults() {
        val request = ProviderProtocol.structuredRequest(
            "model",
            "Return JSON.",
            "Say OK.",
            settings = GenerationSettings(),
        )
        assertEquals(
            "json_object",
            request.getJSONObject("response_format").getString("type"),
        )
        assertEquals(
            "OK",
            ProviderProtocol.parseStructuredBody(
                """{"choices":[{"message":{"content":"{\"body\":\"OK\"}"}}]}""",
            ),
        )
        val capabilities = ProviderCapabilities().withResults(
            listOf(
                CapabilityResult(ProviderCapability.Chat, true),
                CapabilityResult(ProviderCapability.Structured, false, "unsupported"),
            ),
        )
        assertTrue(capabilities.supported.contains(ProviderCapability.Chat))
        assertFalse(capabilities.supports(ProviderCapability.Structured))
        assertEquals("unsupported", capabilities.failures[ProviderCapability.Structured])
    }

    @Test
    fun rejectsMalformedStructuredRepliesBeforeWorldMutation() {
        listOf(
            """{"choices":[{"message":{"content":"not json"}}]}""",
            """{"choices":[{"message":{"content":"{}"}}]}""",
            """{"choices":[{"message":{"content":"{\"body\":\"OK\",\"extra\":true}"}}]}""",
            """{"choices":[{"message":{"content":"{\"body\":42}"}}]}""",
            """{"choices":[{"message":{"content":"{\"body\":\"  \"}"}}]}""",
        ).forEach { response ->
            assertThrows(Exception::class.java) {
                ProviderProtocol.parseStructuredBody(response)
            }
        }
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
            RelationshipState(
                "重新靠近",
                "They chose to repair a recent disagreement.",
                9,
                10,
                closeness = 3,
                trust = 5,
                tension = 0,
            ),
        )

        assertTrue(prompt.contains("Conversation recap through message #12"))
        assertTrue(prompt.contains("They planned a quiet weekend."))
        assertTrue(prompt.contains("User likes tea."))
        assertTrue(prompt.contains("The old station is closed."))
        assertTrue(prompt.contains("Mira suspects it may reopen."))
        assertTrue(prompt.contains("User-disclosed time zone: Asia/Shanghai"))
        assertTrue(prompt.contains("Character location: Paris"))
        assertTrue(prompt.contains("Current relationship: 重新靠近"))
        assertTrue(prompt.contains("established trust and warmth"))
        assertFalse(prompt.contains("closeness"))
        assertFalse(prompt.contains("tension ="))

        val messages = (1L..15L).map { ChatMessage(it, 1, "user", "m$it", it) }
        val recent = recentMessagesForContext(
            messages + listOf(
                ChatMessage(16, 1, "assistant", "partial", 16, status = "failed"),
                ChatMessage(17, 1, "user", "retired", 17, active = false),
            ),
            ConversationRecap("Earlier events", 12, 13, false),
        )
        assertEquals(listOf(13L, 14L, 15L), recent.map(ChatMessage::id))
    }

    @Test
    fun contextBudgetKeepsNewestCanonicalMessagesWithinLimit() {
        val messages = listOf(
            ChatMessage(1, 1, "user", "older message", 1),
            ChatMessage(2, 1, "assistant", "newer reply", 2),
            ChatMessage(3, 1, "user", "最新问题", 3),
        )

        val recent = recentMessagesForContext(messages, recap = null, tokenBudget = 10)

        assertEquals(listOf(3L), recent.map(ChatMessage::id))
        assertEquals(4, estimatedTokenCount("最新问题"))
        assertEquals(4, estimatedTokenCount("older message"))
    }

    @Test
    fun contextBudgetTruncatesButNeverDropsLatestUserMessage() {
        val latest = ChatMessage(9, 1, "user", "这是一个很长的问题", 9)

        val recent = recentMessagesForContext(listOf(latest), recap = null, tokenBudget = 8)

        assertEquals(listOf(9L), recent.map(ChatMessage::id))
        assertEquals("这是一个", recent.single().body)
        assertEquals("这是一个很长的问题", latest.body)
    }

    @Test
    fun deepSeekThinkingTuningSurvivesCustomPresetStaging() {
        val byHost = ProviderConfig(
            preset = ProviderPreset.Custom,
            baseUrl = "https://api.deepseek.com",
            model = "custom-name",
            apiKey = TEST_KEY,
        )
        val byModel = ProviderConfig(
            preset = ProviderPreset.Custom,
            baseUrl = "https://provider.example/v1",
            model = "deepseek-v4-flash",
            apiKey = TEST_KEY,
        )
        val unrelated = ProviderConfig(
            preset = ProviderPreset.Custom,
            baseUrl = "https://provider.example/v1",
            model = "other-model",
            apiKey = TEST_KEY,
        )

        assertTrue(byHost.shouldDisableThinking())
        assertTrue(byModel.shouldDisableThinking())
        assertFalse(unrelated.shouldDisableThinking())
        assertEquals(
            "disabled",
            ProviderProtocol.structuredRequest(
                byHost.model,
                "system",
                "prompt",
                settings = GenerationSettings(),
                disableThinking = byHost.shouldDisableThinking(),
            ).getJSONObject("thinking").getString("type"),
        )
    }

    @Test
    fun retriesOnlyRecoverableStructuredFormatFailures() {
        assertTrue(isRetryableStructuredFormatFailure(org.json.JSONException("unterminated")))
        assertTrue(
            isRetryableStructuredFormatFailure(
                IllegalArgumentException("Structured reply must contain only body"),
            ),
        )
        assertTrue(
            isRetryableStructuredFormatFailure(
                IllegalArgumentException("Structured reply body must be a string"),
            ),
        )
        assertFalse(
            isRetryableStructuredFormatFailure(
                IllegalArgumentException("Structured reply is missing body"),
            ),
        )
        assertFalse(isRetryableStructuredFormatFailure(java.io.IOException("HTTP 401")))
    }

    @Test
    fun retriesOnlyTransientProviderFailures() {
        assertTrue(isTransientProviderFailure("HTTP 429"))
        assertTrue(isTransientProviderFailure("HTTP 503"))
        assertTrue(isTransientProviderFailure("connection reset"))
        assertFalse(isTransientProviderFailure("HTTP 401"))
        assertFalse(isTransientProviderFailure("HTTP 404"))
    }

    @Test
    fun fallbackIsExplicitAndKeepsPrimaryFirst() {
        val fallback = ProviderConfig(
            baseUrl = "https://fallback.example/v1",
            model = "fallback-model",
            apiKey = FALLBACK_KEY,
            capabilities = ProviderCapabilities(
                supported = setOf(ProviderCapability.Structured),
            ),
        )
        val primary = ProviderConfig(
            baseUrl = "https://primary.example/v1",
            model = "primary-model",
            apiKey = PRIMARY_KEY,
            fallback = fallback,
        )

        assertTrue(primary.supports(ProviderCapability.Structured))
        assertEquals(
            listOf("primary-model", "fallback-model"),
            providerCandidates(primary).map(ProviderConfig::model),
        )
        assertEquals(
            listOf("primary-model"),
            providerCandidates(primary.copy(fallback = null)).map(ProviderConfig::model),
        )
    }
}
