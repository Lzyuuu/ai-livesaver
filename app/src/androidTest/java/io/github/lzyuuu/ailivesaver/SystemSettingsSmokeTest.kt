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
        // V4.51 索引重构：主列表 10 行（settingsPrimaryOrder）。
        val roots = listOf("general", "models_engine", "cloud_llm_image", "voice", "instruction", "generation", "memory", "backups", "cleanup", "developer")
        roots.forEach { root ->
            composeRule.onNodeWithTag("me-settings-list")
                .performScrollToNode(hasTestTag("me-setting-$root"))
            composeRule.onNodeWithTag("me-setting-$root").assertIsDisplayed()
        }

        composeRule.onNodeWithTag("me-settings-list")
            .performScrollToNode(hasTestTag("me-setting-voice"))
        composeRule.onNodeWithTag("me-setting-voice").assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("voice-calls-settings", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("voice-calls-settings", useUnmergedTree = true).assertIsDisplayed()

        // The settings screen is a lazy list; verify the actual storage entry is reachable
        // through the same user-visible settings surface rather than assuming fixed layout.
        composeRule.onNodeWithText("返回", useUnmergedTree = true).performClick()
        // V4.51 索引无搜索框；storage 在扩展分组，直接滚动进入。
        composeRule.onNodeWithTag("me-settings-list")
            .performScrollToNode(hasTestTag("me-setting-storage"))
        composeRule.onNodeWithTag("me-setting-storage").assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("storage-settings-screen", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("storage-settings-screen", useUnmergedTree = true).assertIsDisplayed()

        // V4.51 索引不再含 identity/local_dream/imaging 行；扩展分组直达外观页。
        composeRule.onNodeWithText("返回", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("me-settings-list")
            .performScrollToNode(hasTestTag("me-setting-appearance"))
        composeRule.onNodeWithTag("me-setting-appearance").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-appearance-screen", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithText("返回", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("me-settings-list")
            .performScrollToNode(hasTestTag("me-setting-privacy"))
        composeRule.onNodeWithTag("me-setting-privacy").assertIsDisplayed()
        composeRule.onNodeWithTag("me-settings-list")
            .performScrollToNode(hasTestTag("me-setting-update"))
        composeRule.onNodeWithTag("me-setting-update").assertIsDisplayed()
    }

}
