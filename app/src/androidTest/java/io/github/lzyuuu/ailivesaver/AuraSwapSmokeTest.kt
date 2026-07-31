package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuraSwapSmokeTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedDesktopShell() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        writeWelcomeGuideCompleted(context, true)
        WorldStore(context).use { DesktopSeed.ensureDesktopWorld(it, "焰宇", "issue-30") }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun opensAuraSwapAndModelStoreEmptyDirectoryPath() {
        composeRule.onNodeWithTag("desktop-hub-creative_suite").performClick()
        composeRule.onNodeWithTag("hub-app-aura_swap").performClick()
        composeRule.onNodeWithTag("aura-swap-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("aura-run").performClick()
        composeRule.onNodeWithTag("aura-status").assertIsDisplayed()
        composeRule.onNodeWithTag("aura-store-toggle").performClick()
        composeRule.onNodeWithTag("hf-installed").assertIsDisplayed()
        composeRule.onNodeWithTag("hf-download").assertIsDisplayed()
    }
}
