package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CharacterAppearanceCardTest {

    @Test
    fun appearanceFieldsSavedAndMappedInProfileFieldsAndExport() {
        val character = ResidentCharacter(
            id = 42L,
            name = "Seraphina",
            persona = "An ancient guardian of the clocktower",
            attentionTier = "special_focus",
            appearance = "silver hair, golden eyes, slender",
            clothing = "clockmaker velvet coat",
            negativePrompt = "blurry, deformed",
            cardJson = CharacterCardV2.buildCardJson(
                name = "Seraphina",
                fields = CharacterProfileFields(
                    handle = "seraphina",
                    description = "An ancient guardian of the clocktower",
                    personality = "Quiet, attentive",
                    visualStyle = "Painted",
                    gender = "Woman",
                    appearancePrompt = "silver hair, golden eyes, slender",
                    clothing = "clockmaker velvet coat",
                    negativePrompt = "blurry, deformed",
                ),
            ),
        )

        val fields = CharacterCardV2.profileFields(character)
        assertEquals("seraphina", fields.handle)
        assertEquals("Painted", fields.visualStyle)
        assertEquals("Woman", fields.gender)
        assertEquals("silver hair, golden eyes, slender", fields.appearanceSupplement)
        assertEquals("Painted, Woman, silver hair, golden eyes, slender", fields.appearancePrompt)
        assertEquals("clockmaker velvet coat", fields.clothing)
        assertEquals("blurry, deformed", fields.negativePrompt)

        val exportedJson = CharacterCardV2.export(character)
        val parsed = CharacterCardV2.parse(exportedJson.toByteArray())
        assertEquals("Seraphina", parsed.name)
        assertEquals("Painted", parsed.visualStyle)
        assertEquals("Woman", parsed.gender)
        assertEquals("silver hair, golden eyes, slender", parsed.appearanceSupplement)
        assertEquals("Painted, Woman, silver hair, golden eyes, slender", parsed.appearancePrompt)
        assertEquals("clockmaker velvet coat", parsed.clothing)
        assertEquals("blurry, deformed", parsed.negativePrompt)
    }

    @Test
    fun styleAndGenderPresetsAppliedCorrectly() {
        for (style in VisualIdentity.style) {
            for (gender in VisualIdentity.gender) {
                val fields = CharacterProfileFields(
                    handle = "hero",
                    description = "A test hero",
                    visualStyle = style,
                    gender = gender,
                    appearancePrompt = "spiky hair",
                    clothing = "casual jacket",
                )
                val json = CharacterCardV2.buildCardJson("Hero", fields)
                val parsed = CharacterCardV2.parse(json.toByteArray())
                assertEquals(style, parsed.visualStyle)
                assertEquals(gender, parsed.gender)
                assertEquals("spiky hair", parsed.appearanceSupplement)
                assertEquals("$style, $gender, spiky hair", parsed.appearancePrompt)
                assertEquals("casual jacket", parsed.clothing)
            }
        }
    }

    @Test
    fun fillFromDescriptionExtractsKeywords() {
        val descEn = "A slender young woman with short curly blonde hair and amber eyes, wearing a white uniform and boots."
        val extractedEn = CharacterCardV2.extractAppearanceKeywords(descEn, "She is very disciplined.")
        assertEquals("woman", extractedEn.gender)
        assertTrue(extractedEn.appearancePrompt.contains("curly blonde hair") || extractedEn.appearancePrompt.contains("blonde hair"))
        assertTrue(extractedEn.appearancePrompt.contains("amber eyes"))
        assertTrue(extractedEn.clothing.contains("white uniform") || extractedEn.clothing.contains("uniform"))

        val descZh = "修长挺拔的黑发青年，拥有深邃的蓝眸。平日总穿着黑色风衣，二次元写实风格。"
        val extractedZh = CharacterCardV2.extractAppearanceKeywords(descZh, "沉稳可靠的调查员。")
        assertEquals("man", extractedZh.gender)
        assertTrue(extractedZh.appearancePrompt.contains("黑发"))
        assertTrue(extractedZh.appearancePrompt.contains("蓝眸"))
        assertTrue(extractedZh.clothing.contains("黑色风衣"))
    }

    @Test
    fun characterCardV2JsonAndPngPreserveAllVisualIdentityExtensions() {
        val originalFields = CharacterProfileFields(
            handle = "elena",
            description = "An astronomer in the high observatory",
            personality = "Dreamy, brilliant",
            scenario = "Starlit observatory",
            firstMessage = "Look through the lens with me.",
            relationship = "companion",
            visualStyle = "Film",
            gender = "Woman",
            appearancePrompt = "wavy dark brown hair, sapphire eyes, delicate features",
            clothing = "midnight blue gown with silver celestial embroidery",
            negativePrompt = "blurry, low quality, bad hands, distorted",
        )
        val composed = "Film, Woman, wavy dark brown hair, sapphire eyes, delicate features"
        val cardJson = CharacterCardV2.buildCardJson("Elena", originalFields)

        val parsedJson = CharacterCardV2.parse(cardJson.toByteArray())
        assertEquals("Elena", parsedJson.name)
        assertEquals("Film", parsedJson.visualStyle)
        assertEquals("Woman", parsedJson.gender)
        assertEquals(originalFields.appearancePrompt, parsedJson.appearanceSupplement)
        assertEquals(composed, parsedJson.appearancePrompt)
        assertEquals(originalFields.clothing, parsedJson.clothing)
        assertEquals(originalFields.negativePrompt, parsedJson.negativePrompt)

        val dummyPng = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+" +
                "3MxZ5wAAAABJRU5ErkJggg==",
        )
        val parsedPng = CharacterCardV2.parse(CharacterCardV2.embedJson(dummyPng, cardJson))

        assertEquals("Elena", parsedPng.name)
        assertEquals("Film", parsedPng.visualStyle)
        assertEquals("Woman", parsedPng.gender)
        assertEquals(originalFields.appearancePrompt, parsedPng.appearanceSupplement)
        assertEquals(composed, parsedPng.appearancePrompt)
        assertEquals(originalFields.clothing, parsedPng.clothing)
        assertEquals(originalFields.negativePrompt, parsedPng.negativePrompt)
        assertEquals("elena", parsedPng.handle)
        assertEquals("companion", parsedPng.relationship)
    }

    @Test
    fun rootProtectionLocksPersonaWhileUpdatingAppearance() {
        // Builtin Root character
        val initialRoot = ResidentCharacter(
            id = 1L,
            name = "Root",
            persona = DesktopSeed.ROOT_PERSONA,
            attentionTier = "special_focus",
            appearance = "short silver hair, blue eyes",
            clothing = "black high-collar coat",
            negativePrompt = "blurry, bad anatomy",
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
                    negativePrompt = "blurry, bad anatomy",
                ),
            ),
        )

        val initialFields = CharacterCardV2.profileFields(initialRoot)
        assertEquals("root", initialFields.handle)
        assertEquals("Photoreal", initialFields.visualStyle)
        assertEquals("Woman", initialFields.gender)

        val updatedFields = initialFields.copy(
            visualStyle = "Anime",
            clothing = "cybernetic jacket",
            hair = "short silver bob hair",
            eyes = "bright cyan eyes",
        )

        val updatedCardJson = CharacterCardV2.buildCardJson("Root", updatedFields, initialRoot.cardJson)
        val updatedRoot = initialRoot.copy(
            appearance = CharacterCardV2.composeFixedFeature(updatedFields),
            clothing = updatedFields.clothing,
            cardJson = updatedCardJson,
        )

        assertEquals("Root", updatedRoot.name)
        assertEquals(DesktopSeed.ROOT_PERSONA, updatedRoot.persona)

        val reloadedFields = CharacterCardV2.profileFields(updatedRoot)
        assertEquals("Anime", reloadedFields.visualStyle)
        assertEquals("Woman", reloadedFields.gender)
        assertEquals("cybernetic jacket", reloadedFields.clothing)
        assertEquals("short silver bob hair", reloadedFields.hair)
        assertEquals("bright cyan eyes", reloadedFields.eyes)
        assertTrue(reloadedFields.appearancePrompt.contains("short silver bob hair"))
        assertTrue(reloadedFields.appearancePrompt.contains("bright cyan eyes"))

        // Export and verify
        val exported = CharacterCardV2.export(updatedRoot)
        val parsedExport = CharacterCardV2.parse(exported.toByteArray())
        assertEquals("Root", parsedExport.name)
        assertEquals("Anime", parsedExport.visualStyle)
        assertEquals("cybernetic jacket", parsedExport.clothing)
        assertTrue(parsedExport.persona.contains("{{char}}") || parsedExport.persona.contains("Fancy AI"))
    }

    @Test
    fun composesClassificationsThenSupplementInFixedFeatureOrder() {
        val fields = CharacterProfileFields(
            visualStyle = "photoreal",
            gender = "woman",
            age = "early twenties",
            ethnicity = "East Asian",
            skin = "pale",
            eyes = "brown",
            hair = "long black",
            body = "slim",
            appearanceSupplement = "faint scar at the brow",
            clothing = "school uniform",
            negativePrompt = "blurry",
        )
        assertEquals(
            "photoreal, woman, early twenties, East Asian, pale, brown, long black, slim, faint scar at the brow",
            CharacterCardV2.composeFixedFeature(fields),
        )
        assertEquals(
            "photoreal, woman, East Asian, brown, long black, slim",
            CharacterCardV2.composeFixedFeature(fields.copy(age = "", skin = "", appearanceSupplement = "")),
        )
    }

    @Test
    fun ethnicityChipsMatchReferenceMeasurement() {
        assertEquals(
            listOf(
                "Black",
                "East Asian",
                "South Asian",
                "Middle Eastern",
                "Latino",
                "Slavic",
                "White",
                "mixed",
            ),
            VisualIdentity.ethnicity,
        )
    }

    @Test
    fun characterCardJsonAndPngRoundTripPreserveClassificationsAndComposedPrompt() {
        val originalFields = CharacterProfileFields(
            handle = "elena",
            description = "An astronomer in the high observatory",
            personality = "Dreamy, brilliant",
            visualStyle = "film photo",
            gender = "woman",
            age = "thirties",
            ethnicity = "Latino",
            skin = "olive",
            eyes = "brown",
            hair = "blonde waves",
            body = "curvy",
            appearanceSupplement = "constellation freckles",
            clothing = "midnight blue gown",
            negativePrompt = "blurry, low quality",
        )
        val composed =
            "film photo, woman, thirties, Latino, olive, brown, blonde waves, curvy, constellation freckles"
        val cardJson = CharacterCardV2.buildCardJson("Elena", originalFields)
        val visualIdentity = org.json.JSONObject(cardJson)
            .getJSONObject("data")
            .getJSONObject("extensions")
            .getJSONObject("visual_identity")
        assertEquals("film photo", visualIdentity.getString("style"))
        assertEquals("woman", visualIdentity.getString("gender"))
        assertEquals("thirties", visualIdentity.getString("age"))
        assertEquals("Latino", visualIdentity.getString("ethnicity"))
        assertEquals("olive", visualIdentity.getString("skin"))
        assertEquals("brown", visualIdentity.getString("eyes"))
        assertEquals("blonde waves", visualIdentity.getString("hair"))
        assertEquals("curvy", visualIdentity.getString("body"))
        assertEquals("constellation freckles", visualIdentity.getString("supplement"))
        assertEquals(composed, visualIdentity.getString("prompt"))

        val parsedJson = CharacterCardV2.parse(cardJson.toByteArray())
        assertEquals("film photo", parsedJson.visualStyle)
        assertEquals("woman", parsedJson.gender)
        assertEquals("thirties", parsedJson.age)
        assertEquals("Latino", parsedJson.ethnicity)
        assertEquals("olive", parsedJson.skin)
        assertEquals("brown", parsedJson.eyes)
        assertEquals("blonde waves", parsedJson.hair)
        assertEquals("curvy", parsedJson.body)
        assertEquals("constellation freckles", parsedJson.appearanceSupplement)
        assertEquals(composed, parsedJson.appearancePrompt)
        assertEquals("midnight blue gown", parsedJson.clothing)

        val dummyPng = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+" +
                "3MxZ5wAAAABJRU5ErkJggg==",
        )
        val parsedPng = CharacterCardV2.parse(CharacterCardV2.embedJson(dummyPng, cardJson))
        assertEquals("thirties", parsedPng.age)
        assertEquals("Latino", parsedPng.ethnicity)
        assertEquals("constellation freckles", parsedPng.appearanceSupplement)
        assertEquals(composed, parsedPng.appearancePrompt)
    }

    @Test
    fun promptOnlyImportLandsInAppearanceSupplement() {
        val card = """
            {
              "spec":"chara_card_v2",
              "spec_version":"2.0",
              "data":{
                "name":"Aria",
                "description":"A wandering musician",
                "extensions":{
                  "visual_identity":{
                    "style":"Film",
                    "gender":"Woman",
                    "prompt":"long wavy brown hair, hazel eyes, slender"
                  }
                }
              }
            }
        """.trimIndent()
        val parsed = CharacterCardV2.parse(card.toByteArray())
        assertEquals("Film", parsed.visualStyle)
        assertEquals("Woman", parsed.gender)
        assertEquals("long wavy brown hair, hazel eyes, slender", parsed.appearanceSupplement)
        assertEquals("", parsed.hair)
        assertEquals("", parsed.eyes)
        assertEquals("", parsed.body)
        assertEquals(
            "Film, Woman, long wavy brown hair, hazel eyes, slender",
            parsed.appearancePrompt,
        )
    }

    @Test
    fun fillFromDescriptionFillsOnlyEmptyGroupsAndFields() {
        val current = CharacterProfileFields(
            visualStyle = "anime",
            gender = "woman",
            hair = "short dark",
            clothing = "black coat",
            negativePrompt = "blurry",
            appearanceSupplement = "keep me",
        )
        val extracted = ExtractedAppearance(
            appearancePrompt = "should not replace supplement",
            clothing = "red dress",
            visualStyle = "photoreal",
            gender = "man",
            negativePrompt = "deformed",
            hair = "blonde waves",
            eyes = "blue",
            build = "athletic",
            age = "thirties",
            ethnicity = "Slavic",
            skin = "pale",
            body = "athletic",
            supplement = "new extra",
        )
        val filled = CharacterCardV2.fillEmptyVisualIdentity(current, extracted)
        assertEquals("anime", filled.visualStyle)
        assertEquals("woman", filled.gender)
        assertEquals("thirties", filled.age)
        assertEquals("Slavic", filled.ethnicity)
        assertEquals("pale", filled.skin)
        assertEquals("blue", filled.eyes)
        assertEquals("short dark", filled.hair)
        assertEquals("athletic", filled.body)
        assertEquals("black coat", filled.clothing)
        assertEquals("blurry", filled.negativePrompt)
        assertEquals("keep me", filled.appearanceSupplement)
        assertEquals(
            "anime, woman, thirties, Slavic, pale, blue, short dark, athletic, keep me",
            filled.appearancePrompt,
        )
    }
}
