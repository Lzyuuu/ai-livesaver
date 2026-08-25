package io.github.lzyuuu.ailivesaver

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationSettingsTest {
    @Test fun presetsProvide451Values() {
        assertEquals(0.2f, GenerationPreset.Precise.temperature)
        assertEquals(0.7f, GenerationPreset.Balanced.temperature)
        assertEquals(1.1f, GenerationPreset.Creative.temperature)
        assertEquals(2048, GenerationSettings().maxTokens)
    }

    @Test fun chatRequestContainsGenerationParametersAndTemplate() {
        val settings = GenerationSettings(0.4f, 512, 0.8f, instructionTemplate = InstructionTemplate.Direct)
        val request = ProviderProtocol.chatRequest("m", "hello", settings, stream = true)
        assertEquals(0.4, request.getDouble("temperature"), 0.001)
        assertEquals(512, request.getInt("max_tokens"))
        assertEquals(0.8, request.getDouble("top_p"), 0.001)
        assertTrue(request.getJSONArray("messages").getJSONObject(0).getString("content").contains("Answer directly"))
    }

    @Test fun generationSettingsCodecRoundTrips() {
        val settings = GenerationSettings(
            1.15f,
            4096,
            0.8f,
            GenerationPreset.Creative,
            InstructionTemplate.StraightAnswers,
        ).normalized()
        assertEquals(settings, decodeGenerationSettings(encodeGenerationSettings(settings)))
    }

    @Test fun generationSettingsDecodeFallsBackToDefaults() {
        assertEquals(GenerationSettings(), decodeGenerationSettings(null))
        assertEquals(GenerationSettings(), decodeGenerationSettings(""))
        assertEquals(GenerationSettings(), decodeGenerationSettings("not json"))
        assertEquals(
            GenerationSettings(),
            decodeGenerationSettings("""{"temperature":"oops","max_tokens":2048,"top_p":0.95}"""),
        )
    }

    @Test fun generationOverridesCodecRoundTripsEveryField() {
        val overrides = GenerationOverrides(
            temperature = 0.3f,
            maxTokens = 777,
            topP = 0.5f,
            preset = GenerationPreset.Precise,
            instructionTemplate = InstructionTemplate.Direct,
        )
        assertEquals(overrides, decodeGenerationOverrides(encodeGenerationOverrides(overrides)))
    }

    @Test fun generationOverridesCodecKeepsUnsetFieldsEmpty() {
        assertEquals(GenerationOverrides(), decodeGenerationOverrides(null))
        assertEquals(GenerationOverrides(), decodeGenerationOverrides("{}"))
        assertEquals(
            GenerationOverrides(maxTokens = 1024),
            decodeGenerationOverrides("""{"max_tokens":1024}"""),
        )
    }

    @Test fun effectiveGenerationInheritsGlobalWhenNothingIsOverridden() {
        val global = GenerationSettings(1.15f, 4096, 0.8f, GenerationPreset.Creative, InstructionTemplate.Direct)
        assertEquals(global.normalized(), effectiveGeneration(global, GenerationOverrides()))
        assertEquals(global.normalized(), effectiveGeneration(global, null))
    }

    @Test fun effectiveGenerationMergesPartialOverridesWithGlobalDefaults() {
        val global = GenerationSettings(0.7f, 2048, 0.95f, GenerationPreset.Balanced, InstructionTemplate.Roleplay)
        val resolved = effectiveGeneration(
            global,
            GenerationOverrides(temperature = 0.2f, instructionTemplate = InstructionTemplate.StraightAnswers),
        )
        assertEquals(0.2f, resolved.temperature)
        assertEquals(2048, resolved.maxTokens)
        assertEquals(0.95f, resolved.topP)
        assertEquals(GenerationPreset.Balanced, resolved.preset)
        assertEquals(InstructionTemplate.StraightAnswers, resolved.instructionTemplate)
    }

    @Test fun effectiveGenerationPrefersFullOverridesOverGlobalDefaults() {
        val global = GenerationSettings(0.7f, 2048, 0.95f, GenerationPreset.Balanced, InstructionTemplate.Roleplay)
        val overrides = GenerationOverrides(
            temperature = 1.3f,
            maxTokens = 8192,
            topP = 0.6f,
            preset = GenerationPreset.Creative,
            instructionTemplate = InstructionTemplate.Direct,
        )
        assertEquals(
            GenerationSettings(1.3f, 8192, 0.6f, GenerationPreset.Creative, InstructionTemplate.Direct).normalized(),
            effectiveGeneration(global, overrides),
        )
    }

    @Test fun effectiveGenerationNormalizesResolvedValues() {
        val resolved = effectiveGeneration(
            GenerationSettings(),
            GenerationOverrides(temperature = 9f, maxTokens = 1, topP = -3f),
        )
        assertEquals(2f, resolved.temperature)
        assertEquals(128, resolved.maxTokens)
        assertEquals(0f, resolved.topP)
    }

    @Test fun streamingBodyCarriesEffectiveGenerationParameters() {
        val messages = JSONArray()
            .put(org.json.JSONObject().put("role", "system").put("content", "system prompt"))
        val body = ProviderProtocol.streamingChatBody(
            "m",
            messages,
            GenerationSettings(0.4f, 512, 0.8f).normalized(),
        )
        assertEquals("m", body.getString("model"))
        assertEquals(true, body.getBoolean("stream"))
        assertEquals(messages, body.getJSONArray("messages"))
        assertEquals(0.4, body.getDouble("temperature"), 0.001)
        assertEquals(512, body.getInt("max_tokens"))
        assertEquals(0.8, body.getDouble("top_p"), 0.001)
    }

    @Test fun structuredRequestInjectsTemplateHeaderAndSampling() {
        val request = ProviderProtocol.structuredRequest(
            "m",
            "Extract durable facts.",
            "I love cats.",
            settings = GenerationSettings(0.2f, 1024, 0.9f, instructionTemplate = InstructionTemplate.StraightAnswers),
        )
        assertEquals(0.2, request.getDouble("temperature"), 0.001)
        assertEquals(0.9, request.getDouble("top_p"), 0.001)
        val system = request.getJSONArray("messages").getJSONObject(0)
        assertEquals("system", system.getString("role"))
        assertTrue(system.getString("content").contains(InstructionTemplate.StraightAnswers.prompt))
        assertTrue(system.getString("content").contains("Extract durable facts."))
    }

    @Test fun visionRequestInjectsTemplateAsSystemMessage() {
        val request = ProviderProtocol.visionRequest(
            "m",
            "data:image/png;base64,AAAA",
            settings = GenerationSettings(0.5f, 256, 0.7f, instructionTemplate = InstructionTemplate.Direct),
        )
        assertEquals(0.5, request.getDouble("temperature"), 0.001)
        assertEquals(0.7, request.getDouble("top_p"), 0.001)
        val messages = request.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals(InstructionTemplate.Direct.prompt, messages.getJSONObject(0).getString("content"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
    }

    @Test fun applyInstructionTemplateAppendsPromptOnce() {
        val composed = applyInstructionTemplate("base", InstructionTemplate.Roleplay)
        assertEquals("base\n\n${InstructionTemplate.Roleplay.prompt}", composed)
    }
}
