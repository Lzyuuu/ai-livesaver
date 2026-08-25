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
        assertEquals("silver hair, golden eyes, slender", fields.appearancePrompt)
        assertEquals("clockmaker velvet coat", fields.clothing)
        assertEquals("blurry, deformed", fields.negativePrompt)

        val exportedJson = CharacterCardV2.export(character)
        val parsed = CharacterCardV2.parse(exportedJson.toByteArray())
        assertEquals("Seraphina", parsed.name)
        assertEquals("Painted", parsed.visualStyle)
        assertEquals("Woman", parsed.gender)
        assertEquals("silver hair, golden eyes, slender", parsed.appearancePrompt)
        assertEquals("clockmaker velvet coat", parsed.clothing)
        assertEquals("blurry, deformed", parsed.negativePrompt)
    }

    @Test
    fun styleAndGenderPresetsAppliedCorrectly() {
        val styles = listOf("Photoreal", "Film", "Anime", "Painted")
        val genders = listOf("Woman", "Man", "Non-binary", "Unspecified")

        for (style in styles) {
            for (gender in genders) {
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
                assertEquals("spiky hair", parsed.appearancePrompt)
                assertEquals("casual jacket", parsed.clothing)
            }
        }
    }

    @Test
    fun fillFromDescriptionExtractsKeywords() {
        val descEn = "A slender young woman with short curly blonde hair and amber eyes, wearing a white uniform and boots."
        val extractedEn = CharacterCardV2.extractAppearanceKeywords(descEn, "She is very disciplined.")
        assertEquals("Woman", extractedEn.gender)
        assertTrue(extractedEn.appearancePrompt.contains("curly blonde hair") || extractedEn.appearancePrompt.contains("blonde hair"))
        assertTrue(extractedEn.appearancePrompt.contains("amber eyes"))
        assertTrue(extractedEn.clothing.contains("white uniform") || extractedEn.clothing.contains("uniform"))

        val descZh = "修长挺拔的黑发青年，拥有深邃的蓝眸。平日总穿着黑色风衣，二次元写实风格。"
        val extractedZh = CharacterCardV2.extractAppearanceKeywords(descZh, "沉稳可靠的调查员。")
        assertEquals("Man", extractedZh.gender)
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

        val cardJson = CharacterCardV2.buildCardJson("Elena", originalFields)

        // 1. JSON parse round-trip
        val parsedJson = CharacterCardV2.parse(cardJson.toByteArray())
        assertEquals("Elena", parsedJson.name)
        assertEquals("Film", parsedJson.visualStyle)
        assertEquals("Woman", parsedJson.gender)
        assertEquals(originalFields.appearancePrompt, parsedJson.appearancePrompt)
        assertEquals(originalFields.clothing, parsedJson.clothing)
        assertEquals(originalFields.negativePrompt, parsedJson.negativePrompt)

        // 2. PNG embed & extract round-trip
        val dummyPng = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+" +
                "3MxZ5wAAAABJRU5ErkJggg==",
        )
        val embeddedPng = CharacterCardV2.embedJson(dummyPng, cardJson)
        val parsedPng = CharacterCardV2.parse(embeddedPng)

        assertEquals("Elena", parsedPng.name)
        assertEquals("Film", parsedPng.visualStyle)
        assertEquals("Woman", parsedPng.gender)
        assertEquals(originalFields.appearancePrompt, parsedPng.appearancePrompt)
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

        // User updates visual appearance in Tab 2 to Anime style and changes clothing
        val updatedFields = initialFields.copy(
            visualStyle = "Anime",
            clothing = "cybernetic jacket",
            appearancePrompt = "short silver bob hair, bright cyan eyes",
        )

        val updatedCardJson = CharacterCardV2.buildCardJson("Root", updatedFields, initialRoot.cardJson)
        val updatedRoot = initialRoot.copy(
            appearance = updatedFields.appearancePrompt,
            clothing = updatedFields.clothing,
            cardJson = updatedCardJson,
        )

        // Verify root persona is locked and not corrupted
        assertEquals("Root", updatedRoot.name)
        assertEquals(DesktopSeed.ROOT_PERSONA, updatedRoot.persona)

        // Verify appearance was updated
        val reloadedFields = CharacterCardV2.profileFields(updatedRoot)
        assertEquals("Anime", reloadedFields.visualStyle)
        assertEquals("Woman", reloadedFields.gender)
        assertEquals("cybernetic jacket", reloadedFields.clothing)
        assertEquals("short silver bob hair, bright cyan eyes", reloadedFields.appearancePrompt)

        // Export and verify
        val exported = CharacterCardV2.export(updatedRoot)
        val parsedExport = CharacterCardV2.parse(exported.toByteArray())
        assertEquals("Root", parsedExport.name)
        assertEquals("Anime", parsedExport.visualStyle)
        assertEquals("cybernetic jacket", parsedExport.clothing)
        assertTrue(parsedExport.persona.contains("{{char}}") || parsedExport.persona.contains("Fancy AI"))
    }
}
