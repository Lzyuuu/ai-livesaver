package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #54 联调契约：把 #51/#52/#53 已落地的纯函数串成一条「角色 → 生成参数 → 聊天控制 → 配图意图」链路。
 * 不含真实网络；云端流式与 Release 安装由模拟器验收覆盖。
 */
class EndToEndFlowTest {
    @Test
    fun importedAppearanceFeedsGenerationPayloadAndImageIntent() {
        val fields = CharacterProfileFields(
            handle = "mira",
            description = "A cartographer of dream cities",
            personality = "Curious",
            firstMessage = "The roads shift when you sleep.",
            relationship = "friend",
            visualStyle = "anime",
            gender = "woman",
            eyes = "amber",
            hair = "silver",
            appearanceSupplement = "braided hair",
            clothing = "oilskin coat",
            negativePrompt = "photorealistic, extra fingers",
        )
        val imported = CharacterCardV2.parse(
            CharacterCardV2.buildCardJson("Mira", fields).toByteArray(),
        )
        assertEquals("Mira", imported.name)
        assertEquals("anime", imported.visualStyle)
        assertEquals("woman", imported.gender)
        assertEquals("amber", imported.eyes)
        assertEquals("silver", imported.hair)
        assertEquals("braided hair", imported.appearanceSupplement)
        assertEquals("anime, woman, amber, silver, braided hair", imported.appearancePrompt)
        assertEquals("oilskin coat", imported.clothing)

        val effective = effectiveGeneration(
            GenerationSettings(),
            GenerationOverrides(temperature = 0.4f),
        )
        val request = ProviderProtocol.chatRequest("deepseek-v4-flash", "hello", effective, stream = true)
        assertEquals(0.4, request.getDouble("temperature"), 0.001)
        assertEquals(1024, request.getInt("max_tokens"))
        assertEquals(0.95, request.getDouble("top_p"), 0.001)
        assertTrue(
            request.getJSONArray("messages").getJSONObject(0).getString("content")
                .contains(FACTORY_INSTRUCTION_ROLEPLAY_BODY),
        )

        val controls = defaultChatControls().copy(autoImageGeneration = true)
        assertTrue(shouldQueueReplyImage(controls, "The harbor lights come on."))
        assertFalse(shouldQueueReplyImage(defaultChatControls(), "The harbor lights come on."))
        assertEquals(
            "anime, woman, amber, silver, braided hair, oilskin coat, moonlight over the harbor",
            composeImageIntentPrompt(
                imported.appearancePrompt,
                imported.clothing,
                "",
                "moonlight over the harbor",
            ),
        )
        assertTrue(postAllowed(controls, Y_POST_KIND))
        assertTrue(postAllowed(controls, "moment"))
        assertFalse(postAllowed(controls.copy(allowPostY = false), Y_POST_KIND))
    }

    @Test
    fun messengerControlsMemorySearchAndExitGuardCompose() {
        val defaults = defaultChatControls()
        assertFalse(defaults.webSearchEnabled)
        assertFalse(defaults.autoImageGeneration)
        assertTrue(defaults.allowPostY)
        assertTrue(defaults.allowPostUstagram)
        assertTrue(defaults.allowPostRebbit)

        val restored = decodeChatControls(
            encodeChatControls(defaults.copy(webSearchEnabled = true, allowPostRebbit = false)),
        )
        assertTrue(restored.webSearchEnabled)
        assertFalse(restored.allowPostRebbit)
        assertTrue(restored.allowPostY)

        val prompt = buildChatSystemPrompt(
            character = ResidentCharacter(id = 2L, name = "Aria", persona = "curious"),
            memories = emptyList(),
            recap = null,
            webResults = listOf(WebSearchResult("Harbor", "Sunset over still water")),
        )
        assertTrue(prompt.contains("- Harbor: Sunset over still water"))

        assertTrue(shouldConfirmChatExit(sending = true, activeReply = false))
        assertTrue(shouldConfirmChatExit(sending = false, activeReply = true))
        assertFalse(shouldConfirmChatExit(sending = false, activeReply = false))

        val user = ChatMessage(11L, 2L, "user", "remember this", 1L)
        val assistant = ChatMessage(12L, 2L, "assistant", "ok", 2L)
        assertEquals(11L, latestUserMessageForMemory(listOf(user, assistant))?.id)
        assertEquals(null, latestUserMessageForMemory(listOf(assistant)))
    }
}
