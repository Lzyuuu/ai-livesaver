package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneContactsSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()
    private var rootId = 0L

    @Before
    fun seedDesktopShell() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        writeWelcomeGuideCompleted(context, true)
        WorldStore(context).use { store ->
            DesktopSeed.ensureDesktopWorld(store, "焰宇", "smoke", context)
            rootId = store.characters().first { it.name == "Root" }.id
        }
        seedInstallOnHomeForSmoke(context, listOf("phone"))
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun phoneContactDialsIntoMatchingMessengerConversation() {
        openDesktopAppFromGrid(composeRule, "phone")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Phone", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Root", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("phone-search").performTextInput("Root")
        composeRule.onAllNodesWithText("Root", useUnmergedTree = true)[0].assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("拨号", useUnmergedTree = true)[0].performClick()
        composeRule.waitForIdle()
        val listVisible = composeRule.onAllNodesWithTag("messenger-list").fetchSemanticsNodes().isNotEmpty()
        if (listVisible) composeRule.onNodeWithText("Root", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Root", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("messenger-conversation").assertIsDisplayed()
    }
}
