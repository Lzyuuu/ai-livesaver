package io.github.lzyuuu.ailivesaver

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationSettingsTest {
    @Test fun presetsProvide451Values() {
        assertEquals(0.4f, GenerationPreset.Precise.temperature)
        assertEquals(1024, GenerationPreset.Precise.maxTokens)
        assertEquals(0.10f, GenerationPreset.Precise.minP)
        assertEquals(0.0f, GenerationPreset.Precise.xtcSurprise)
        assertEquals(1.05f, GenerationPreset.Precise.repetitionPenalty)
        assertEquals(0.80f, GenerationPreset.Balanced.temperature)
        assertEquals(1024, GenerationPreset.Balanced.maxTokens)
        assertEquals(0.05f, GenerationPreset.Balanced.minP)
        assertEquals(0.0f, GenerationPreset.Balanced.xtcSurprise)
        assertEquals(1.0f, GenerationPreset.Balanced.repetitionPenalty)
        assertEquals(1.05f, GenerationPreset.Creative.temperature)
        assertEquals(1024, GenerationPreset.Creative.maxTokens)
        assertEquals(0.02f, GenerationPreset.Creative.minP)
        assertEquals(0.5f, GenerationPreset.Creative.xtcSurprise)
        assertEquals(1.03f, GenerationPreset.Creative.repetitionPenalty)
        assertEquals(0.80f, GenerationSettings().temperature)
        assertEquals(1024, GenerationSettings().maxTokens)
        assertEquals(0.95f, GenerationSettings().topP)
        assertEquals(GenerationPreset.Balanced, GenerationSettings().preset)
        assertEquals(0.05f, GenerationSettings().expertSampling.minP)
        assertEquals(1.0f, GenerationSettings().expertSampling.repetitionPenalty)
        assertEquals(64, GenerationSettings().expertSampling.penaltyWindow)
        assertEquals(0.0f, GenerationSettings().expertSampling.xtcSurprise)
        assertEquals(0.10f, GenerationSettings().expertSampling.xtcFloor)
        assertEquals(0.0f, GenerationSettings().expertSampling.dryLoopBreaker)
        assertEquals(1.75f, GenerationSettings().expertSampling.drySteepness)
        assertEquals(2, GenerationSettings().expertSampling.dryAllowedRepeat)
        assertEquals(0.0f, GenerationSettings().expertSampling.dynamicTemperature)
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

    @Test fun uncustomizedOldFactoryBalancedMigratesToReferenceBalanced() {
        val migrated = decodeGenerationSettings(
            """{"temperature":0.7,"max_tokens":2048,"top_p":0.95,"preset":"Balanced","instruction_template":"Roleplay"}""",
        )
        assertEquals(0.80f, migrated.temperature)
        assertEquals(1024, migrated.maxTokens)
        assertEquals(0.95f, migrated.topP)
        assertEquals(GenerationPreset.Balanced, migrated.preset)
        assertEquals(ExpertSampling(), migrated.expertSampling)
        assertEquals(FACTORY_INSTRUCTION_ROLEPLAY_ID, migrated.instructionTemplateId)
        assertEquals(factoryInstructionLibrary(), migrated.instructionLibrary)
    }

    @Test fun customizedSamplingNumbersStayUnmigrated() {
        val custom = decodeGenerationSettings(
            """{"temperature":0.7,"max_tokens":2048,"top_p":0.90,"preset":"Custom","instruction_template":"Direct"}""",
        )
        assertEquals(0.7f, custom.temperature)
        assertEquals(2048, custom.maxTokens)
        assertEquals(0.90f, custom.topP)
        assertEquals(GenerationPreset.Custom, custom.preset)
        val preciseLike = decodeGenerationSettings(
            """{"temperature":0.4,"max_tokens":512,"top_p":0.8,"preset":"Balanced","instruction_template":"Direct"}""",
        )
        assertEquals(0.4f, preciseLike.temperature)
        assertEquals(512, preciseLike.maxTokens)
        assertEquals(0.8f, preciseLike.topP)
    }

    @Test fun expertSamplingCodecRoundTripsAsGlobalPreference() {
        val expert = ExpertSampling(
            minP = 0.12f,
            repetitionPenalty = 1.12f,
            penaltyWindow = 128,
            xtcSurprise = 0.35f,
            xtcFloor = 0.2f,
            dryLoopBreaker = 0.8f,
            drySteepness = 2.25f,
            dryAllowedRepeat = 4,
            dynamicTemperature = 0.3f,
        )
        val settings = GenerationSettings(
            temperature = 1.05f,
            maxTokens = 1024,
            topP = 0.95f,
            preset = GenerationPreset.Creative,
            expertSampling = expert,
        ).normalized()
        val encoded = encodeGenerationSettings(settings)
        val restored = decodeGenerationSettings(encoded)
        assertEquals(settings, restored)
        val json = JSONObject(encoded).getJSONObject("expert_sampling")
        assertEquals(0.12, json.getDouble("min_p"), 0.0001)
        assertEquals(1.12, json.getDouble("repetition_penalty"), 0.0001)
        assertEquals(128, json.getInt("penalty_window"))
        assertEquals(0.35, json.getDouble("xtc_probability"), 0.0001)
        assertEquals(0.2, json.getDouble("xtc_threshold"), 0.0001)
        assertEquals(0.8, json.getDouble("dry_multiplier"), 0.0001)
        assertEquals(2.25, json.getDouble("dry_base"), 0.0001)
        assertEquals(4, json.getInt("dry_allowed_length"))
        assertEquals(0.3, json.getDouble("dynatemp_range"), 0.0001)
        val overrides = encodeGenerationOverrides(
            GenerationOverrides(temperature = 0.4f, preset = GenerationPreset.Precise),
        )
        assertFalse(overrides.contains("expert_sampling"))
        assertFalse(overrides.contains("min_p"))
        assertFalse(overrides.contains("xtc_probability"))
        val resolved = effectiveGeneration(
            settings,
            GenerationOverrides(temperature = 0.2f, maxTokens = 512),
        )
        assertEquals(0.2f, resolved.temperature)
        assertEquals(512, resolved.maxTokens)
        assertEquals(expert.normalized(), resolved.expertSampling)
    }

    @Test fun cloudPayloadOmitsExpertSamplingKeys() {
        val settings = GenerationSettings(
            expertSampling = ExpertSampling(
                minP = 0.2f,
                repetitionPenalty = 1.2f,
                penaltyWindow = 256,
                xtcSurprise = 0.5f,
                xtcFloor = 0.25f,
                dryLoopBreaker = 1.1f,
                drySteepness = 3f,
                dryAllowedRepeat = 6,
                dynamicTemperature = 0.4f,
            ),
        )
        val payloads = listOf(
            ProviderProtocol.chatRequest("m", "hello", settings, stream = true),
            ProviderProtocol.streamingChatBody("m", JSONArray(), settings),
            ProviderProtocol.structuredRequest("m", "sys", "user", settings = settings),
            ProviderProtocol.visionRequest("m", "data:image/png;base64,AAAA", settings = settings),
        )
        val forbidden = listOf(
            "min_p",
            "xtc_probability",
            "xtc_threshold",
            "repetition_penalty",
            "penalty_window",
            "dry_multiplier",
            "dry_base",
            "dry_allowed_length",
            "dynatemp_range",
            "expert_sampling",
        )
        payloads.forEach { payload ->
            forbidden.forEach { key ->
                assertFalse(payload.has(key))
                assertFalse(payload.toString().contains(key))
            }
            assertTrue(payload.has("temperature"))
            assertTrue(payload.has("max_tokens"))
            assertTrue(payload.has("top_p"))
        }
    }

    @Test fun resetRestoresBalancedSamplingWithoutWipingInstructionLibrary() {
        val customLibrary = factoryInstructionLibrary() + InstructionTemplateEntry(
            "user-keep",
            "Keep me",
            "custom speaking style",
            factory = false,
        )
        val dirty = GenerationSettings(
            temperature = 1.4f,
            maxTokens = 4096,
            topP = 0.5f,
            preset = GenerationPreset.Custom,
            instructionTemplateId = "user-keep",
            instructionLibrary = customLibrary,
            imagePromptTemplate = "CHANGED IMAGE PROMPT TEMPLATE",
            expertSampling = ExpertSampling(
                minP = 0.2f,
                repetitionPenalty = 1.3f,
                penaltyWindow = 256,
                xtcSurprise = 0.6f,
                xtcFloor = 0.3f,
                dryLoopBreaker = 1.5f,
                drySteepness = 3.5f,
                dryAllowedRepeat = 8,
                dynamicTemperature = 0.7f,
            ),
        )
        val reset = resetGenerationSampling(dirty)
        assertEquals(0.80f, reset.temperature)
        assertEquals(1024, reset.maxTokens)
        assertEquals(0.95f, reset.topP)
        assertEquals(GenerationPreset.Balanced, reset.preset)
        assertEquals(ExpertSampling(), reset.expertSampling)
        assertEquals("user-keep", reset.instructionTemplateId)
        assertEquals(customLibrary, reset.instructionLibrary)
        assertEquals("CHANGED IMAGE PROMPT TEMPLATE", reset.imagePromptTemplate)
    }

    @Test fun namedPresetUpdatesOwnedKnobsAndLeavesDryDynatempWindow() {
        val previous = GenerationSettings(
            temperature = 0.80f,
            maxTokens = 1024,
            topP = 0.95f,
            preset = GenerationPreset.Balanced,
            expertSampling = ExpertSampling(
                minP = 0.05f,
                repetitionPenalty = 1.0f,
                penaltyWindow = 256,
                xtcSurprise = 0.0f,
                xtcFloor = 0.22f,
                dryLoopBreaker = 1.2f,
                drySteepness = 2.5f,
                dryAllowedRepeat = 5,
                dynamicTemperature = 0.4f,
            ),
        )
        val precise = applyNamedGenerationPreset(previous, GenerationPreset.Precise)
        assertEquals(GenerationPreset.Precise, precise.preset)
        assertEquals(0.4f, precise.temperature)
        assertEquals(1024, precise.maxTokens)
        assertEquals(0.8f, precise.topP)
        assertEquals(0.10f, precise.expertSampling.minP)
        assertEquals(0.0f, precise.expertSampling.xtcSurprise)
        assertEquals(1.05f, precise.expertSampling.repetitionPenalty)
        assertEquals(256, precise.expertSampling.penaltyWindow)
        assertEquals(0.22f, precise.expertSampling.xtcFloor)
        assertEquals(1.2f, precise.expertSampling.dryLoopBreaker)
        assertEquals(2.5f, precise.expertSampling.drySteepness)
        assertEquals(5, precise.expertSampling.dryAllowedRepeat)
        assertEquals(0.4f, precise.expertSampling.dynamicTemperature)
        val creative = applyNamedGenerationPreset(precise, GenerationPreset.Creative)
        assertEquals(GenerationPreset.Creative, creative.preset)
        assertEquals(1.05f, creative.temperature)
        assertEquals(1024, creative.maxTokens)
        assertEquals(0.95f, creative.topP)
        assertEquals(0.02f, creative.expertSampling.minP)
        assertEquals(0.5f, creative.expertSampling.xtcSurprise)
        assertEquals(1.03f, creative.expertSampling.repetitionPenalty)
        assertEquals(256, creative.expertSampling.penaltyWindow)
        assertEquals(0.22f, creative.expertSampling.xtcFloor)
        assertEquals(1.2f, creative.expertSampling.dryLoopBreaker)
        assertEquals(2.5f, creative.expertSampling.drySteepness)
        assertEquals(5, creative.expertSampling.dryAllowedRepeat)
        assertEquals(0.4f, creative.expertSampling.dynamicTemperature)
        val custom = applyNamedGenerationPreset(creative, GenerationPreset.Custom)
        assertEquals(GenerationPreset.Custom, custom.preset)
        assertEquals(creative.temperature, custom.temperature)
        assertEquals(creative.maxTokens, custom.maxTokens)
        assertEquals(creative.topP, custom.topP)
        assertEquals(creative.expertSampling, custom.expertSampling)
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
