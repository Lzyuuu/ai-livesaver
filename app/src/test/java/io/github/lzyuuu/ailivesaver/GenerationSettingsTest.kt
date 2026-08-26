package io.github.lzyuuu.ailivesaver

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationSettingsTest {
    @Test fun presetsProvide451Values() {
        assertEquals(0.2f, GenerationPreset.Precise.temperature)
        assertEquals(0.7f, GenerationPreset.Balanced.temperature)
        assertEquals(1.1f, GenerationPreset.Creative.temperature)
        assertEquals(2048, GenerationSettings().maxTokens)
    }

    @Test fun factoryInstructionLibraryMatchesReferenceV451() {
        val library = factoryInstructionLibrary()
        assertEquals(
            listOf("Roleplay", "Direct", "StraightAnswers"),
            library.map(InstructionTemplateEntry::id),
        )
        assertEquals(
            listOf("Roleplay", "Direct", "Straight answers"),
            library.map(InstructionTemplateEntry::name),
        )
        assertEquals(
            "Engage as {{char}}, be creative. Embrace the character's personality, emotion, mental state. Consider the character's history and mood.",
            library.single { it.id == FACTORY_INSTRUCTION_ROLEPLAY_ID }.body,
        )
        assertEquals(
            "You are {{char}}, talking with {{user}}. Be sharp, warm and direct. Keep your answers short and concise.",
            library.single { it.id == FACTORY_INSTRUCTION_DIRECT_ID }.body,
        )
        assertEquals(
            "You are {{char}}, answer what {{user}} actually asked with one or two sentences at top.",
            library.single { it.id == FACTORY_INSTRUCTION_STRAIGHT_ID }.body,
        )
        assertTrue(library.all(InstructionTemplateEntry::factory))
        assertEquals(45, instructionTokenHint(FACTORY_INSTRUCTION_ROLEPLAY_BODY))
        assertFalse(FACTORY_INSTRUCTION_ROLEPLAY_BODY.contains("Stay deeply in character"))
        assertFalse(FACTORY_INSTRUCTION_DIRECT_BODY.contains("Answer directly"))
        assertFalse(FACTORY_INSTRUCTION_STRAIGHT_BODY.contains("Give straightforward"))
    }

    @Test fun chatRequestContainsGenerationParametersAndTemplate() {
        val settings = GenerationSettings(0.4f, 512, 0.8f, instructionTemplateId = FACTORY_INSTRUCTION_DIRECT_ID)
        val request = ProviderProtocol.chatRequest("m", "hello", settings, stream = true)
        assertEquals(0.4, request.getDouble("temperature"), 0.001)
        assertEquals(512, request.getInt("max_tokens"))
        assertEquals(0.8, request.getDouble("top_p"), 0.001)
        assertTrue(request.getJSONArray("messages").getJSONObject(0).getString("content").contains("Be sharp, warm and direct"))
        assertFalse(request.has("min_p"))
        assertFalse(request.toString().contains("min_p"))
    }

    @Test fun generationSettingsCodecRoundTrips() {
        val settings = GenerationSettings(
            1.15f,
            4096,
            0.8f,
            GenerationPreset.Creative,
            FACTORY_INSTRUCTION_STRAIGHT_ID,
        ).normalized()
        assertEquals(settings, decodeGenerationSettings(encodeGenerationSettings(settings)))
    }

    @Test fun instructionLibraryRoundTripsEditedAndUserTemplates() {
        val custom = InstructionTemplateEntry("user-1", "Warm Roleplay", "Stay playful as {{char}}.", factory = false)
        val library = factoryInstructionLibrary().map { entry ->
            if (entry.id == FACTORY_INSTRUCTION_ROLEPLAY_ID) entry.copy(body = "Edited factory Roleplay.") else entry
        } + custom
        val settings = GenerationSettings(
            instructionTemplateId = custom.id,
            instructionLibrary = library,
            imagePromptTemplate = "custom image prompt",
        )
        val restored = decodeGenerationSettings(encodeGenerationSettings(settings))
        assertEquals(settings, restored)
        assertEquals("Edited factory Roleplay.", restored.instructionLibrary.single { it.id == FACTORY_INSTRUCTION_ROLEPLAY_ID }.body)
        assertEquals(custom, restored.instructionLibrary.single { it.id == "user-1" })
        assertEquals("custom image prompt", restored.imagePromptTemplate)
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

    @Test fun oldEnumOnlyJsonMapsOntoFactoryLibraryBodies() {
        val decoded = decodeGenerationSettings(
            """{"temperature":0.4,"max_tokens":512,"top_p":0.8,"preset":"Balanced","instruction_template":"Direct"}""",
        )
        assertEquals(FACTORY_INSTRUCTION_DIRECT_ID, decoded.instructionTemplateId)
        assertEquals(factoryInstructionLibrary(), decoded.instructionLibrary)
        assertEquals(FACTORY_INSTRUCTION_DIRECT_BODY, decoded.selectedInstructionBody())
        assertEquals(FACTORY_IMAGE_PROMPT_TEMPLATE, decoded.imagePromptTemplate)
    }

    @Test fun generationOverridesCodecRoundTripsEveryField() {
        val overrides = GenerationOverrides(
            temperature = 0.3f,
            maxTokens = 777,
            topP = 0.5f,
            preset = GenerationPreset.Precise,
            instructionTemplateId = FACTORY_INSTRUCTION_DIRECT_ID,
        )
        val encoded = encodeGenerationOverrides(overrides)
        assertEquals(overrides, decodeGenerationOverrides(encoded))
        assertFalse(encoded.contains("body"))
        assertFalse(encoded.contains("Stay deeply"))
        assertEquals(FACTORY_INSTRUCTION_DIRECT_ID, JSONObject(encoded).getString("instruction_template"))
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
        val global = GenerationSettings(1.15f, 4096, 0.8f, GenerationPreset.Creative, FACTORY_INSTRUCTION_DIRECT_ID)
        assertEquals(global.normalized(), effectiveGeneration(global, GenerationOverrides()))
        assertEquals(global.normalized(), effectiveGeneration(global, null))
    }

    @Test fun effectiveGenerationMergesPartialOverridesWithGlobalDefaults() {
        val global = GenerationSettings(0.7f, 2048, 0.95f, GenerationPreset.Balanced, FACTORY_INSTRUCTION_ROLEPLAY_ID)
        val resolved = effectiveGeneration(
            global,
            GenerationOverrides(temperature = 0.2f, instructionTemplateId = FACTORY_INSTRUCTION_STRAIGHT_ID),
        )
        assertEquals(0.2f, resolved.temperature)
        assertEquals(2048, resolved.maxTokens)
        assertEquals(0.95f, resolved.topP)
        assertEquals(GenerationPreset.Balanced, resolved.preset)
        assertEquals(FACTORY_INSTRUCTION_STRAIGHT_ID, resolved.instructionTemplateId)
        assertEquals(global.instructionLibrary, resolved.instructionLibrary)
    }

    @Test fun effectiveGenerationPrefersFullOverridesOverGlobalDefaults() {
        val global = GenerationSettings(0.7f, 2048, 0.95f, GenerationPreset.Balanced, FACTORY_INSTRUCTION_ROLEPLAY_ID)
        val overrides = GenerationOverrides(
            temperature = 1.3f,
            maxTokens = 8192,
            topP = 0.6f,
            preset = GenerationPreset.Creative,
            instructionTemplateId = FACTORY_INSTRUCTION_DIRECT_ID,
        )
        assertEquals(
            GenerationSettings(1.3f, 8192, 0.6f, GenerationPreset.Creative, FACTORY_INSTRUCTION_DIRECT_ID).normalized(),
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
        assertFalse(body.has("min_p"))
        assertFalse(body.has("xtc_probability"))
    }

    @Test fun structuredRequestInjectsTemplateHeaderAndSampling() {
        val request = ProviderProtocol.structuredRequest(
            "m",
            "Extract durable facts.",
            "I love cats.",
            settings = GenerationSettings(0.2f, 1024, 0.9f, instructionTemplateId = FACTORY_INSTRUCTION_STRAIGHT_ID),
        )
        assertEquals(0.2, request.getDouble("temperature"), 0.001)
        assertEquals(0.9, request.getDouble("top_p"), 0.001)
        val system = request.getJSONArray("messages").getJSONObject(0)
        assertEquals("system", system.getString("role"))
        assertTrue(system.getString("content").contains(FACTORY_INSTRUCTION_STRAIGHT_BODY))
        assertTrue(system.getString("content").contains("Extract durable facts."))
    }

    @Test fun visionRequestInjectsTemplateAsSystemMessage() {
        val request = ProviderProtocol.visionRequest(
            "m",
            "data:image/png;base64,AAAA",
            settings = GenerationSettings(0.5f, 256, 0.7f, instructionTemplateId = FACTORY_INSTRUCTION_DIRECT_ID),
        )
        assertEquals(0.5, request.getDouble("temperature"), 0.001)
        assertEquals(0.7, request.getDouble("top_p"), 0.001)
        val messages = request.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals(FACTORY_INSTRUCTION_DIRECT_BODY, messages.getJSONObject(0).getString("content"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
    }

    @Test fun applyInstructionTemplateAppendsPromptOnce() {
        val composed = applyInstructionTemplate("base", GenerationSettings())
        assertEquals("base\n\n$FACTORY_INSTRUCTION_ROLEPLAY_BODY", composed)
    }

    @Test fun selectedLibraryBodyIsInjectedAsSystemHeader() {
        val settings = GenerationSettings(
            instructionTemplateId = "user-night",
            instructionLibrary = factoryInstructionLibrary() + InstructionTemplateEntry(
                id = "user-night",
                name = "Night desk",
                body = "Speak like a night-shift archivist.",
                factory = false,
            ),
        )
        val chat = ProviderProtocol.chatRequest("m", "hello", settings)
        assertEquals(
            "Speak like a night-shift archivist.",
            chat.getJSONArray("messages").getJSONObject(0).getString("content"),
        )
        val structured = ProviderProtocol.structuredRequest(
            "m",
            "Extract durable facts.",
            "I love cats.",
            settings = settings,
        )
        assertTrue(
            structured.getJSONArray("messages").getJSONObject(0).getString("content")
                .startsWith("Extract durable facts.\n\nSpeak like a night-shift archivist."),
        )
    }

    @Test fun missingInstructionIdFallsBackToFactoryRoleplay() {
        val settings = GenerationSettings(instructionTemplateId = "missing-id")
        assertEquals(FACTORY_INSTRUCTION_ROLEPLAY_BODY, settings.selectedInstructionBody())
        assertEquals("Roleplay", settings.selectedInstructionName())
        val overrideResolved = effectiveGeneration(
            GenerationSettings(),
            GenerationOverrides(instructionTemplateId = "gone"),
        )
        assertEquals(FACTORY_INSTRUCTION_ROLEPLAY_BODY, overrideResolved.selectedInstructionBody())
        val request = ProviderProtocol.chatRequest("m", "hello", overrideResolved)
        assertEquals(
            FACTORY_INSTRUCTION_ROLEPLAY_BODY,
            request.getJSONArray("messages").getJSONObject(0).getString("content"),
        )
    }

    @Test fun restoreFactoryDeletesUserTemplatesAndReselectsRoleplay() {
        val dirty = GenerationSettings(
            instructionTemplateId = "user-1",
            instructionLibrary = factoryInstructionLibrary().map { entry ->
                if (entry.id == FACTORY_INSTRUCTION_DIRECT_ID) entry.copy(body = "tweaked") else entry
            } + InstructionTemplateEntry("user-1", "Mine", "custom body", factory = false),
        )
        val restored = restoreFactoryInstructionLibrary(dirty)
        assertEquals(factoryInstructionLibrary(), restored.instructionLibrary)
        assertEquals(FACTORY_INSTRUCTION_ROLEPLAY_ID, restored.instructionTemplateId)
        assertEquals(FACTORY_INSTRUCTION_DIRECT_BODY, restored.instructionLibrary.single { it.id == FACTORY_INSTRUCTION_DIRECT_ID }.body)
        assertTrue(restored.instructionLibrary.none { !it.factory })
        assertEquals(dirty.imagePromptTemplate, restored.imagePromptTemplate)
        assertEquals(dirty.temperature, restored.temperature)
    }

    @Test fun saveAsNewKeepsOriginalAndAddsNamedVariant() {
        val original = GenerationSettings()
        val (library, newId) = saveInstructionTemplateAsNew(
            original.instructionLibrary,
            "A warmer take.",
            "Roleplay",
            newId = "user-2",
        )
        assertEquals(4, library.size)
        assertEquals(FACTORY_INSTRUCTION_ROLEPLAY_BODY, library.single { it.id == FACTORY_INSTRUCTION_ROLEPLAY_ID }.body)
        val created = library.single { it.id == "user-2" }
        assertEquals("Roleplay 2", created.name)
        assertEquals("A warmer take.", created.body)
        assertFalse(created.factory)
        assertEquals("user-2", newId)
        val afterDeleteSelected = deleteInstructionTemplate(library, selectedId = "user-2", deleteId = "user-2")
        assertEquals(library, afterDeleteSelected)
        val afterDeleteOther = deleteInstructionTemplate(library, selectedId = "user-2", deleteId = FACTORY_INSTRUCTION_DIRECT_ID)
        assertEquals(3, afterDeleteOther.size)
        assertTrue(afterDeleteOther.none { it.id == FACTORY_INSTRUCTION_DIRECT_ID })
    }

    @Test fun imagePromptPersistsWithoutChangingCompose() {
        val settings = GenerationSettings(imagePromptTemplate = "CHANGED IMAGE PROMPT TEMPLATE")
        val restored = decodeGenerationSettings(encodeGenerationSettings(settings))
        assertEquals("CHANGED IMAGE PROMPT TEMPLATE", restored.imagePromptTemplate)
        assertEquals(
            "silver hair, black coat, moonlight over Tokyo",
            composeImageIntentPrompt(" silver hair ", "black coat", "", "moonlight over Tokyo"),
        )
        val chat = ProviderProtocol.chatRequest("m", "hello", restored)
        assertFalse(chat.toString().contains("CHANGED IMAGE PROMPT TEMPLATE"))
        val structured = ProviderProtocol.structuredRequest("m", "sys", "user", settings = restored)
        assertFalse(structured.toString().contains("CHANGED IMAGE PROMPT TEMPLATE"))
    }
}
