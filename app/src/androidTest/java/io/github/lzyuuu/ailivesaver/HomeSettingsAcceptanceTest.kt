package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        writeWelcomeGuideCompleted(context, true)
        WorldStore(context).use { DesktopSeed.ensureDesktopWorld(it, "验收", "test", context) }
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
            rule.onNodeWithTag("settings-root-$root").assertIsDisplayed().performClick()
        }
        rule.onNodeWithTag("me-setting-provider").assertIsDisplayed()
        rule.onNodeWithTag("settings-search").performTextInput("provider")
        rule.onNodeWithTag("me-setting-provider").assertIsDisplayed()
        rule.onNodeWithTag("settings-search").performTextInput("no-such-setting")
        rule.onNodeWithTag("settings-no-results").assertIsDisplayed()
    }
}
