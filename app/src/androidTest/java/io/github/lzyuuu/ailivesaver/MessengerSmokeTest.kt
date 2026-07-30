package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
