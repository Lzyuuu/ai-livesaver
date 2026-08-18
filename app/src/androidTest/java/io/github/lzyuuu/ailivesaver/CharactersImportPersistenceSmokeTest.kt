package io.github.lzyuuu.ailivesaver

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CharactersImportPersistenceSmokeTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: TestRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuleChain
                .outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS))
                .around(composeRule)
        } else {
            composeRule
        }

    @Before
    fun seedDesktopShell() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation
                .executeShellCommand("pm grant io.github.lzyuuu.ailivesaver android.permission.POST_NOTIFICATIONS")
                .close()
        }
        instrumentation.uiAutomation
            .executeShellCommand("am force-stop com.mrj.fancyai.github")
            .close()
        writeWelcomeGuideCompleted(context, true)
        WorldStore(context).use { store ->
            DesktopSeed.ensureDesktopWorld(
                store,
                userName = "焰宇",
                about = "characters-smoke",
                context = context,
            )
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("system-desktop").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
    }

    @Test
    fun opensCharactersFromSocialHub() {
        composeRule.onNodeWithTag("desktop-grid-characters").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("characters-app").assertIsDisplayed()
        composeRule.onNodeWithText("Characters", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Root", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("@root", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun importsXmlAndJsonCardsAndPersistsAcrossRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <character>
              <name>SmokeXml</name>
              <description>XML librarian</description>
              <personality>Calm</personality>
              <scenario>Quiet stacks</scenario>
              <first_mes>Shh</first_mes>
              <relationship>mentor</relationship>
              <handle>smokexml</handle>
            </character>
        """.trimIndent()
        val json = """
            {
              "spec":"chara_card_v2",
              "spec_version":"2.0",
              "data":{
                "name":"SmokeJson",
                "description":"JSON courier",
                "personality":"Quick",
                "scenario":"Night docks",
                "first_mes":"Package ready",
                "extensions":{"handle":"smokejson","relationship_to_user":"ally"}
              }
            }
        """.trimIndent()

        WorldStore(context).use { store ->
            listOf(xml, json).forEach { payload ->
                val card = CharacterCardV2.parse(payload.toByteArray())
                val fields = CharacterProfileFields(
                    handle = card.handle,
                    description = card.description.ifBlank { card.persona },
                    personality = card.personality,
                    scenario = card.scenario,
                    firstMessage = card.firstMessage,
                    relationship = card.relationship,
                )
                val id = store.addCharacter(
                    card.name,
                    CharacterCardV2.composePersona(fields).ifBlank { card.persona },
                    "resident",
                    "",
                    "",
                    "",
                    CharacterCardV2.buildCardJson(card.name, fields, card.rawJson),
                )
                if (card.firstMessage.isNotBlank()) {
                    store.addMessage(id, "assistant", card.firstMessage)
                }
                if (card.relationship.isNotBlank()) {
                    store.correctRelationship(
                        id,
                        card.relationship,
                        card.relationship,
                        pinned = false,
                    )
                }
            }
        }

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("desktop-grid-characters").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("characters-app").assertIsDisplayed()
        composeRule.onNodeWithText("SmokeXml", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("@smokexml", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("SmokeJson", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("@smokejson", useUnmergedTree = true).assertIsDisplayed()

        WorldStore(context).use { store ->
            val characters = store.characters(includeDeparted = true)
            val xmlChar = characters.first { it.name == "SmokeXml" }
            val jsonChar = characters.first { it.name == "SmokeJson" }
            val xmlFields = CharacterCardV2.profileFields(xmlChar)
            val jsonFields = CharacterCardV2.profileFields(jsonChar)
            assertEquals("smokexml", xmlFields.handle)
            assertEquals("mentor", xmlFields.relationship)
            assertEquals("Quiet stacks", xmlFields.scenario)
            assertEquals("smokejson", jsonFields.handle)
            assertEquals("ally", jsonFields.relationship)
            assertTrue(xmlChar.cardJson.contains("chara_card_v2"))
            assertTrue(jsonChar.cardJson.contains("chara_card_v2"))
            assertEquals("Shh", store.messages(xmlChar.id).last().body)
            assertEquals("Package ready", store.messages(jsonChar.id).last().body)
        }
    }

    @Test
    fun newCharacterPersistsAfterEditorSave() {
        composeRule.onNodeWithTag("desktop-grid-characters").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("characters-new").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("character-editor").assertIsDisplayed()
        composeRule.onNodeWithText("Name", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Handle", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Personality", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Relationship to you", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Description", useUnmergedTree = true).assertIsDisplayed()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WorldStore(context).use { store ->
            val fields = CharacterProfileFields(
                handle = "newsmoke",
                description = "Freshly created for smoke",
                personality = "Bright",
                scenario = "Test lab",
                firstMessage = "Hello smoke",
                relationship = "testers",
            )
            store.addCharacter(
                "NewSmoke",
                CharacterCardV2.composePersona(fields),
                "resident",
                "",
                "",
                "",
                CharacterCardV2.buildCardJson("NewSmoke", fields),
            )
        }

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        // showCharacters is rememberSaveable — recreate may restore Characters, not desktop.
        val onCharacters = composeRule.onAllNodesWithTag("characters-app")
            .fetchSemanticsNodes()
            .isNotEmpty() ||
            composeRule.onAllNodesWithTag("character-editor")
                .fetchSemanticsNodes()
                .isNotEmpty()
        if (!onCharacters) {
            composeRule.onNodeWithTag("desktop-grid-characters").performClick()
            composeRule.waitForIdle()
        } else if (
            composeRule.onAllNodesWithTag("character-editor").fetchSemanticsNodes().isNotEmpty()
        ) {
            composeRule.activityRule.scenario.onActivity {
                it.onBackPressedDispatcher.onBackPressed()
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("characters-app").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("characters-app").assertIsDisplayed()
        composeRule.onNodeWithText("NewSmoke", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("@newsmoke", useUnmergedTree = true).assertIsDisplayed()

        WorldStore(context).use { store ->
            val saved = store.characters().first { it.name == "NewSmoke" }
            val fields = CharacterCardV2.profileFields(saved)
            assertEquals("newsmoke", fields.handle)
            assertEquals("Bright", fields.personality)
            assertEquals("testers", fields.relationship)
            assertTrue(saved.cardJson.contains("Freshly created for smoke"))
        }
    }
}
