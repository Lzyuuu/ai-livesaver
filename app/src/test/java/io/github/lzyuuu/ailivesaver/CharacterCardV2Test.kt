package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterCardV2Test {
    private val json = """
        {
          "spec":"chara_card_v2",
          "spec_version":"2.0",
          "data":{
            "name":"Mira",
            "description":"An old friend",
            "personality":"Honest",
            "scenario":"",
            "first_mes":"Welcome back",
            "mes_example":"",
            "character_book":{
              "entries":[
                {"content":"Mira knows the north gate is locked.","enabled":true},
                {"content":"Disabled lore","enabled":false}
              ]
            }
          }
        }
    """.trimIndent()

    @Test
    fun parsesJsonAndPngCards() {
        val parsed = CharacterCardV2.parse(json.toByteArray())
        assertEquals("Mira", parsed.name)
        assertEquals("An old friend\n\nHonest", parsed.persona)
        assertEquals("Welcome back", parsed.firstMessage)
        assertEquals(listOf("Mira knows the north gate is locked."), parsed.lore)

        val transparentPng = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+" +
                "3MxZ5wAAAABJRU5ErkJggg==",
        )
        val embedded = CharacterCardV2.embedJson(transparentPng, json)
        assertEquals("Mira", CharacterCardV2.parse(embedded).name)
    }

    @Test
    fun parsesXmlCharacterCards() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <character>
              <name>Lina</name>
              <description>A quiet librarian</description>
              <personality>Warm</personality>
              <scenario>Rainy bookstore</scenario>
              <first_mes>Welcome in</first_mes>
              <relationship>mentor</relationship>
              <handle>lina</handle>
            </character>
        """.trimIndent()
        val parsed = CharacterCardV2.parse(xml.toByteArray())
        assertEquals("Lina", parsed.name)
        assertEquals("A quiet librarian\n\nWarm\n\nRainy bookstore", parsed.persona)
        assertEquals("Welcome in", parsed.firstMessage)
        assertEquals("mentor", parsed.relationship)
        assertEquals("lina", parsed.handle)
        assertTrue(parsed.rawJson.contains("chara_card_v2"))
    }

    @Test
    fun parsesLegacyJsonCards() {
        val legacy = """
            {
              "char_name":"Kai",
              "char_persona":"A courier",
              "world_scenario":"Night docks",
              "char_greeting":"Package for you"
            }
        """.trimIndent()
        val parsed = CharacterCardV2.parse(legacy.toByteArray())
        assertEquals("Kai", parsed.name)
        assertEquals("A courier\n\nNight docks", parsed.persona)
        assertEquals("Package for you", parsed.firstMessage)
    }

    @Test
    fun preservesVisualIdentityOnJsonAndPngRoundTrip() {
        val fields = CharacterProfileFields(
            handle = "mira",
            description = "A brave photographer",
            personality = "Curious",
            visualStyle = "Anime",
            gender = "Woman",
            appearancePrompt = "short silver hair, blue eyes",
            clothing = "black trench coat",
            negativePrompt = "blurry, deformed",
        )
        val card = CharacterCardV2.buildCardJson("Mira", fields)
        val parsed = CharacterCardV2.parse(card.toByteArray())
        assertEquals("Anime", parsed.visualStyle)
        assertEquals("Woman", parsed.gender)
        assertEquals("short silver hair, blue eyes", parsed.appearancePrompt)
        assertEquals("black trench coat", parsed.clothing)
        assertEquals("blurry, deformed", parsed.negativePrompt)
        val transparentPng = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+" +
                "3MxZ5wAAAABJRU5ErkJggg==",
        )
        val png = CharacterCardV2.embedJson(transparentPng, card)
        val parsedPng = CharacterCardV2.parse(png)
        assertEquals("Anime", parsedPng.visualStyle)
        assertEquals("Woman", parsedPng.gender)
        assertEquals("short silver hair, blue eyes", parsedPng.appearancePrompt)
        assertEquals("black trench coat", parsedPng.clothing)
        assertEquals("blurry, deformed", parsedPng.negativePrompt)
    }

    @Test
    fun parsesVisualIdentityFromNestedExtensionsDictionary() {
        val cardWithDict = """
            {
              "spec":"chara_card_v2",
              "spec_version":"2.0",
              "data":{
                "name":"Aria",
                "description":"A wandering musician",
                "personality":"Melodic",
                "extensions":{
                  "visual_identity":{
                    "style":"Film",
                    "gender":"Woman",
                    "prompt":"long wavy brown hair, hazel eyes, slender",
                    "clothing":"vintage leather jacket",
                    "negative_prompt":"bad anatomy, watermark"
                  }
                }
              }
            }
        """.trimIndent()
        val parsed = CharacterCardV2.parse(cardWithDict.toByteArray())
        assertEquals("Aria", parsed.name)
        assertEquals("Film", parsed.visualStyle)
        assertEquals("Woman", parsed.gender)
        assertEquals("long wavy brown hair, hazel eyes, slender", parsed.appearancePrompt)
        assertEquals("vintage leather jacket", parsed.clothing)
        assertEquals("bad anatomy, watermark", parsed.negativePrompt)
    }

    @Test
    fun parsesVisualIdentityFromSlotFieldsWhenPromptEmpty() {
        val cardWithSlots = """
            {
              "spec":"chara_card_v2",
              "spec_version":"2.0",
              "data":{
                "name":"Rui",
                "description":"A ninja",
                "extensions":{
                  "visual_identity":{
                    "style":"Anime",
                    "gender":"Man",
                    "hair":"short spiky black hair",
                    "eyes":"ruby eyes",
                    "build":"athletic",
                    "clothing":"dark shinobi armor"
                  }
                }
              }
            }
        """.trimIndent()
        val parsed = CharacterCardV2.parse(cardWithSlots.toByteArray())
        assertEquals("Anime", parsed.visualStyle)
        assertEquals("Man", parsed.gender)
        assertTrue(parsed.appearancePrompt.contains("short spiky black hair"))
        assertTrue(parsed.appearancePrompt.contains("ruby eyes"))
        assertTrue(parsed.appearancePrompt.contains("athletic"))
        assertEquals("dark shinobi armor", parsed.clothing)
    }

    @Test
    fun fillsAppearanceFromDescription() {
        val prompt = CharacterCardV2.appearanceFromDescription("A woman with long red hair and green eyes, wearing a black coat")
        assertTrue(prompt.contains("long red hair"))
        assertTrue(prompt.contains("green eyes"))
        assertTrue(prompt.contains("black coat"))
    }

    @Test
    fun extractsAppearanceKeywordsFromEnglishAndChinese() {
        val en = CharacterCardV2.extractAppearanceKeywords(
            "A tall young woman with long silver hair and bright blue eyes",
            "She usually wears a dark trench coat and boots.",
        )
        assertEquals("Woman", en.gender)
        assertEquals("Photoreal", en.visualStyle)
        assertTrue(en.appearancePrompt.contains("long silver hair"))
        assertTrue(en.appearancePrompt.contains("blue eyes"))
        assertTrue(en.appearancePrompt.contains("tall"))
        assertTrue(en.clothing.contains("dark trench coat"))

        val zh = CharacterCardV2.extractAppearanceKeywords(
            "高挑的黑发少女，有着清澈的蓝眸，气质优雅",
            "平日里总是一袭黑色风衣，二次元画风。",
        )
        assertEquals("Woman", zh.gender)
        assertEquals("Anime", zh.visualStyle)
        assertTrue(zh.appearancePrompt.contains("黑发"))
        assertTrue(zh.appearancePrompt.contains("蓝眸"))
        assertTrue(zh.appearancePrompt.contains("高挑"))
        assertTrue(zh.clothing.contains("黑色风衣"))
    }

    @Test
    fun appearanceExtractorParsesLlmJsonOutput() {
        val sampleLlmJson = """
            ```json
            {
              "hair": "long flowing golden hair",
              "eyes": "emerald green eyes",
              "clothing": "white silk dress with gold embroidery",
              "build": "slender and graceful",
              "appearance_prompt": "long flowing golden hair, emerald green eyes, slender and graceful",
              "visual_style": "Painted",
              "gender": "Woman",
              "negative_prompt": "blurry, low quality, artifacts"
            }
            ```
        """.trimIndent()
        val extracted = AppearanceExtractor.parseLlmResponse(sampleLlmJson)
        assertEquals("Painted", extracted.visualStyle)
        assertEquals("Woman", extracted.gender)
        assertEquals("long flowing golden hair, emerald green eyes, slender and graceful", extracted.appearancePrompt)
        assertEquals("white silk dress with gold embroidery", extracted.clothing)
        assertEquals("blurry, low quality, artifacts", extracted.negativePrompt)
    }

    @Test
    fun rootCharacterProfileAndExportProtectsPersonaWhileUpdatingAppearance() {
        val rootCharacter = ResidentCharacter(
            id = 1,
            name = "Root",
            persona = DesktopSeed.ROOT_PERSONA,
            attentionTier = "special_focus",
            appearance = "short silver hair, blue eyes",
            clothing = "black high-collar coat",
            negativePrompt = "blurry, low quality",
            cardJson = CharacterCardV2.buildCardJson(
                "Root",
                CharacterProfileFields(
                    handle = "root",
                    description = DesktopSeed.ROOT_PERSONA,
                    personality = "Steady, system administrator",
                    visualStyle = "Photoreal",
                    gender = "Woman",
                    appearancePrompt = "short silver hair, blue eyes",
                    clothing = "black high-collar coat",
                    negativePrompt = "blurry, low quality",
                ),
            ),
        )
        val fields = CharacterCardV2.profileFields(rootCharacter)
        assertEquals("root", fields.handle)
        assertEquals("Photoreal", fields.visualStyle)
        assertEquals("Woman", fields.gender)
        assertEquals("short silver hair, blue eyes", fields.appearancePrompt)
        assertEquals("black high-collar coat", fields.clothing)

        // Simulate appearance modification
        val updatedFields = fields.copy(
            visualStyle = "Anime",
            clothing = "white lab coat",
        )
        val updatedJson = CharacterCardV2.buildCardJson(rootCharacter.name, updatedFields, rootCharacter.cardJson)
        val updatedCharacter = rootCharacter.copy(
            clothing = "white lab coat",
            cardJson = updatedJson,
        )
        val reExported = CharacterCardV2.export(updatedCharacter)
        val parsedReExport = CharacterCardV2.parse(reExported.toByteArray())
        assertEquals("Anime", parsedReExport.visualStyle)
        assertEquals("white lab coat", parsedReExport.clothing)
        assertEquals("root", parsedReExport.handle)
        // Persona is preserved
        assertTrue(parsedReExport.persona.contains(DesktopSeed.ROOT_PERSONA))
    }

    @Test
    fun preservesUnknownExtensionsOnExport() {
        val exported = CharacterCardV2.export(
            ResidentCharacter(
                id = 1,
                name = "Mira",
                persona = "Updated",
                cardJson = json.replace(
                    "\"mes_example\":\"\"",
                    "\"mes_example\":\"\",\"extensions\":{\"qa_marker\":\"preserve-me\"}",
                ),
            ),
        )
        assertTrue(exported.contains("\"qa_marker\": \"preserve-me\""))
    }
}
