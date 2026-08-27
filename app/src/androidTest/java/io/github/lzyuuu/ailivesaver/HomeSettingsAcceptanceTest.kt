package io.github.lzyuuu.ailivesaver

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeSettingsAcceptanceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun seed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        grantPostNotificationIfNeeded(context)
        seedDesktopShellForSmoke(context)
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    @Test fun desktopHomeAndSettingsAreReachable() {
        rule.onNodeWithTag("system-desktop").assertIsDisplayed()
        rule.onNodeWithTag("desktop-dock-settings").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("me-settings-list").assertIsDisplayed()
        // 主列表 10 行一比一（参考 V4.51 索引顺序）。
        val primary = listOf(
            "general",
            "models_engine",
            "cloud_llm_image",
            "voice",
            "instruction",
            "generation",
            "memory",
            "backups",
            "cleanup",
            "developer",
        )
        primary.forEach { row ->
            rule.onNodeWithTag("me-settings-list")
                .performScrollToNode(hasTestTag("me-setting-$row"))
            rule.onNodeWithTag("me-setting-$row").assertIsDisplayed()
        }
        // 扩展分组存在。
        rule.onNodeWithTag("me-settings-list")
            .performScrollToNode(hasTestTag("settings-extended-section"))
        rule.onNodeWithTag("settings-extended-section").assertIsDisplayed()
        // 打开常规页并返回。
        rule.onNodeWithTag("me-settings-list")
            .performScrollToNode(hasTestTag("me-setting-general"))
        rule.onNodeWithTag("me-setting-general").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("general-settings-screen", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("返回", useUnmergedTree = true).performClick()
        // 打开记忆页。
        rule.onNodeWithTag("me-settings-list")
            .performScrollToNode(hasTestTag("me-setting-memory"))
        rule.onNodeWithTag("me-setting-memory").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("memory-settings-screen", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
