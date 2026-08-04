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
class BinderFlowSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedDesktopShell() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        writeWelcomeGuideCompleted(context, true)
        WorldStore(context).use { store ->
            DesktopSeed.ensureDesktopWorld(store, "焰宇", "smoke", context)
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun binderRejectsBlankThenBuildsMatchAndOpensMessenger() {
        composeRule.onNodeWithTag("desktop-hub-social_hub").performClick()
        composeRule.onNodeWithTag("hub-app-binder").performClick()
        composeRule.onNodeWithText("Binder", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("binder-field-relationship").performTextInput("长期陪伴")
        composeRule.onNodeWithTag("binder-next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("binder-field-personality", useUnmergedTree = true)
            .assertIsDisplayed()
    }
}
