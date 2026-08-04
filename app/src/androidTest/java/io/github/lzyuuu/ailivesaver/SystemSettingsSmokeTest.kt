package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollTo
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
    fun settingsExposeSixCategoriesAndKeyEntries() {
        composeRule.onNodeWithTag("desktop-dock-settings").performClick()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
        listOf("ai-models", "voice-calls", "image-generation", "you-personas", "app", "developer-about").forEach {
            val tag = "settings-category-$it"
            composeRule.onNodeWithTag("me-settings-list").performScrollToNode(hasTestTag(tag))
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
        listOf("me-setting-providers", "me-setting-voice", "me-setting-dream", "me-setting-identity", "me-setting-world", "me-setting-diagnostics").forEach {
            composeRule.onNodeWithTag("me-settings-list").performScrollToNode(hasTestTag(it))
            composeRule.onNodeWithTag(it).assertIsDisplayed()
        }

        composeRule.onNodeWithTag("me-settings-list").performScrollToNode(hasTestTag("me-setting-voice"))
        composeRule.onNodeWithTag("me-setting-voice").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Voice & Calls", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Voice & Calls", useUnmergedTree = true).assertIsDisplayed()

        // The settings screen is a lazy list; verify the actual storage entry is reachable
        // through the same user-visible settings surface rather than assuming fixed layout.
        composeRule.onNodeWithText("返回", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("me-settings-list").performScrollToNode(hasTestTag("me-setting-storage"))
        composeRule.onNodeWithTag("me-setting-storage").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Storage", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Storage", useUnmergedTree = true).assertIsDisplayed()
    }

}
