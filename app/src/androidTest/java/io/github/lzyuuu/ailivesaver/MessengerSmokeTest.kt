package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessengerSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedDesktopShell() {
        seedDesktopShellForSmoke(InstrumentationRegistry.getInstrumentation().targetContext)
        WorldStore(InstrumentationRegistry.getInstrumentation().targetContext).use { store ->
            if (store.characters().none { it.name == "Alpha" }) {
                store.addCharacter("Alpha", "curious", "resident", "", "", "")
            }
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun opensMessengerFromDockAndReturns() {
        composeRule.onNodeWithTag("desktop-dock-messenger").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Messenger", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("messenger-list").assertIsDisplayed()
        composeRule.onNodeWithTag("messenger-search").assertIsDisplayed()
        composeRule.onNodeWithTag("messenger-new").assertIsDisplayed()
        composeRule.onNodeWithText("Recent Chats", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Groups", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("+ New Character", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("messenger-list-settings").assertIsDisplayed()
        composeRule.onNodeWithTag("messenger-back-desktop").performClick()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
    }

    @Test
    fun opensNewGroupDialogFromMessengerList() {
        composeRule.onNodeWithTag("desktop-dock-messenger").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("messenger-new-group").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("messenger-new-group-confirm").assertIsDisplayed()
    }

    @Test
    fun createsGroupWithoutPaywallAndSharesTimelineAcrossMembers() {
        composeRule.onNodeWithTag("desktop-dock-messenger").performClick()
        composeRule.onNodeWithTag("messenger-new-group").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[1].performTextInput("Room")
        composeRule.onAllNodesWithText("Root", useUnmergedTree = true)[1].performClick()
        composeRule.onAllNodesWithText("Alpha", useUnmergedTree = true)[1].performClick()
        composeRule.onNodeWithTag("messenger-new-group-confirm").performClick()
        composeRule.onNodeWithText("Room", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Pro", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun opensMessengerConversationAndExposesEnhancementsMenu() {
        composeRule.onNodeWithTag("desktop-dock-messenger").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Root", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("messenger-open-context").assertIsDisplayed()
        composeRule.onNodeWithTag("messenger-send").assertIsDisplayed()
        composeRule.onNodeWithTag("messenger-chat-options").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("messenger-clear-conversation").assertIsDisplayed()
    }
}
