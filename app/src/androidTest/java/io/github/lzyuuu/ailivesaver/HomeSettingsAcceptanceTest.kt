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
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
        val roots = listOf("chat_brain", "voice_calls", "image_generation", "you_personas", "app", "developer_about", "system_settings", "help_guide", "update")
        roots.forEach { root ->
            rule.onNodeWithTag("me-settings-list")
                .performScrollToNode(hasTestTag("settings-root-$root"))
            rule.onNodeWithTag("settings-root-$root").assertIsDisplayed()
        }
        rule.onNodeWithTag("me-settings-list")
            .performScrollToNode(hasTestTag("me-setting-provider"))
        rule.onNodeWithTag("me-setting-provider").assertIsDisplayed()
        rule.onNodeWithTag("me-settings-list")
            .performScrollToNode(hasTestTag("me-setting-local_dream"))
        rule.onNodeWithTag("me-setting-local_dream").assertIsDisplayed().performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("local-dream-settings-screen", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("返回", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("me-settings-list")
            .performScrollToNode(hasTestTag("settings-search"))
        rule.onNodeWithTag("settings-search").performTextClearance()
        rule.onNodeWithTag("settings-search").performTextInput("identity")
        rule.onNodeWithTag("me-setting-identity").assertIsDisplayed().performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("identity-screen", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("返回", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("settings-search").performTextClearance()
        rule.onNodeWithTag("settings-search").performTextInput("no-such-setting")
        rule.onNodeWithTag("settings-no-results").assertIsDisplayed()
    }
}
