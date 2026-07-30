package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
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
