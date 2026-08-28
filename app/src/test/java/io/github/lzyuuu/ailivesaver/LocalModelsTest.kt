package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelsTest {
    private val manifestJson = """
        {"components": [
            {"id": "gguf-gemma4-e4b", "type": "chat", "name": "Gemma 4 E4B — llama.cpp",
             "quant": "Q4_0", "minRamMb": 6000,
             "url": "https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q4_0.gguf",
             "sizeBytes": 4836002944, "sha256": "4a403d2e4d80281063e4f517b1c061ded8476b4011a4fc2ba7dbff707075547e"},
            {"id": "litert-gemma4-e2b", "type": "litert", "name": "Gemma 4 E2B — LiteRT GPU",
             "quant": "int4", "minRamMb": 6000,
             "url": "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
             "sizeBytes": 2588147712, "sha256": "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"},
            {"id": "whisper-tiny", "type": "asr", "name": "Whisper Tiny",
             "url": "https://example.com/whisper.bin", "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
            {"id": "broken", "type": "chat", "name": "no url", "url": "", "sha256": "bb"}
        ]}
    """.trimIndent()

    @Test
    fun parsesChatAndLitertComponentsOnly() {
        val entries = LocalModels.parseManifest(manifestJson)
        assertEquals(listOf("gguf-gemma4-e4b", "litert-gemma4-e2b"), entries.map { it.id })
        assertTrue(entries.all { it.url.startsWith("https://") })
        assertEquals(4836002944L, entries[0].sizeBytes)
        assertEquals("Q4_0", entries[0].quant)
        assertEquals("gemma-4-E4B-it-Q4_0.gguf", entries[0].fileName)
    }

    @Test
    fun samplingEncodesExpertParamsInJniOrder() {
        val generation = GenerationSettings().copy(
            temperature = 0.7f,
            topP = 0.95f,
            expertSampling = ExpertSampling(
                minP = 0.05f,
                repetitionPenalty = 1.1f,
                penaltyWindow = 64,
                xtcSurprise = 0.2f,
                xtcFloor = 0.1f,
                dryLoopBreaker = 0.8f,
                drySteepness = 1.75f,
                dryAllowedRepeat = 2,
            ),
        )
        val s = LlamaChat.encodeSampling(generation)
        assertEquals(10, s.size)
        assertEquals(0.7f, s[0])
        assertEquals(0.95f, s[1])
        assertEquals(0.05f, s[2])
        assertEquals(1.1f, s[3])
        assertEquals(64f, s[4])
        assertEquals(0.2f, s[5])
        assertEquals(0.1f, s[6])
        assertEquals(0.8f, s[7])
        assertEquals(1.75f, s[8])
        assertEquals(2f, s[9])
    }

    @Test
    fun promptWrapsGemmaTemplateWithHistoryAndInstruction() {
        val prompt = LlamaChat.buildPrompt(
            systemInstruction = "回答保持简短。",
            recent = listOf("user" to "你好", "model" to "你好呀"),
            latestUser = "介绍你自己",
        )
        assertTrue(prompt.startsWith("<start_of_turn>user\n"))
        assertTrue(prompt.contains("回答保持简短。"))
        assertTrue(prompt.contains("用户: 你好"))
        assertTrue(prompt.contains("角色: 你好呀"))
        assertTrue(prompt.endsWith("介绍你自己<end_of_turn>\n<start_of_turn>model\n"))
    }
}
