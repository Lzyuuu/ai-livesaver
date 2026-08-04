package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SystemSettingsSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seed() {
        seedDesktopShellForSmoke(composeRule.activity)
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun settingsExposeNineRootsAndKeyEntries() {
        composeRule.onNodeWithTag("desktop-dock-settings").performClick()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
        val roots = listOf("chat_brain", "voice_calls", "image_generation", "you_personas", "app", "developer_about", "system_settings", "help_guide", "update")
        roots.forEachIndexed { index, root ->
            composeRule.onNodeWithTag("me-settings-list").performScrollToIndex(index + 2)
            composeRule.onNodeWithTag("settings-root-$root").assertIsDisplayed()
        }

        composeRule.onNodeWithTag("me-settings-list").performScrollToIndex(3)
        composeRule.onNodeWithTag("settings-root-voice_calls").performClick()
        composeRule.onNodeWithTag("me-setting-voice").assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Voice & Calls", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Voice & Calls", useUnmergedTree = true).assertIsDisplayed()

        // The settings screen is a lazy list; verify the actual storage entry is reachable
        // through the same user-visible settings surface rather than assuming fixed layout.
        composeRule.onNodeWithText("返回", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("me-settings-list").performScrollToIndex(8)
        composeRule.onNodeWithTag("settings-root-system_settings").performClick()
        composeRule.onNodeWithTag("me-setting-storage").assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Storage", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Storage", useUnmergedTree = true).assertIsDisplayed()
    }

}
