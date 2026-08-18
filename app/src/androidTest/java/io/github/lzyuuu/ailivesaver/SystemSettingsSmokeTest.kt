package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.rules.RuleChain
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SystemSettingsSmokeTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(SeededMainActivityRule())
        .around(composeRule)

    @Before
    fun waitForDesktop() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
    }

    @Test
    fun settingsExposeNineRootsAndKeyEntries() {
        composeRule.onNodeWithTag("desktop-dock-settings").performClick()
        composeRule.onNodeWithTag("me-settings-list").assertIsDisplayed()
        val roots = listOf("chat_brain", "voice_calls", "image_generation", "you_personas", "app", "developer_about", "system_settings", "help_guide", "update")
        roots.forEach { root ->
            composeRule.onNodeWithTag("me-settings-list")
                .performScrollToNode(hasTestTag("settings-root-$root"))
            composeRule.onNodeWithTag("settings-root-$root").assertIsDisplayed()
        }

        composeRule.onNodeWithTag("me-settings-list")
            .performScrollToNode(hasTestTag("me-setting-voice"))
        composeRule.onNodeWithTag("me-setting-voice").assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Voice & Calls", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Voice & Calls", useUnmergedTree = true).assertIsDisplayed()

        // The settings screen is a lazy list; verify the actual storage entry is reachable
        // through the same user-visible settings surface rather than assuming fixed layout.
        composeRule.onNodeWithText("返回", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("settings-search").performTextInput("storage")
        composeRule.onNodeWithTag("me-setting-storage").assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("storage-settings-screen", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("storage-settings-screen", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithText("返回", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("me-settings-list")
            .performScrollToNode(hasTestTag("me-setting-identity"))
        composeRule.onNodeWithTag("me-setting-identity").performClick()
        composeRule.onNodeWithTag("identity-screen", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithText("返回", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("me-settings-list")
            .performScrollToNode(hasTestTag("me-setting-local_dream"))
        composeRule.onNodeWithTag("me-setting-local_dream").performClick()
        composeRule.onNodeWithTag("local-dream-settings-screen", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithText("返回", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("me-settings-list")
            .performScrollToNode(hasTestTag("me-setting-imaging"))
        composeRule.onNodeWithTag("me-setting-imaging").performClick()
        composeRule.onNodeWithTag("imaging-studio", useUnmergedTree = true).assertIsDisplayed()
    }

}
