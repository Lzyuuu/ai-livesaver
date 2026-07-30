package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedDesktopShell() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        writeWelcomeGuideCompleted(context, true)
        context.getSharedPreferences("imaging_studio", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("backend", "on_device")
            .apply()
        WorldStore(context).use { store ->
            DesktopSeed.ensureDesktopWorld(
                store,
                userName = "焰宇",
                about = "smoke",
                context = context,
            )
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun exposesSystemDesktopWithoutFiveTabs() {
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-root-card").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-dock").assertIsDisplayed()
        listOf("World", "Chats", "Moments", "Commons", "Me").forEach { tab ->
            assertTrue(
                "五 Tab 壳层不应出现在系统桌面",
                composeRule.onAllNodesWithText(tab, useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isEmpty(),
            )
        }
        composeRule.onNodeWithText("Messenger", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Root", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun opensSocialHubAppAndReturnsToDesktop() {
        composeRule.onNodeWithTag("desktop-hub-social_hub").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub-app-ustagram").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Ustagram", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("返回桌面", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        // 从 Ustagram 返回会回到 Social Hub
        composeRule.onNodeWithTag("desktop-hub-sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-hub-close").performClick()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
    }

    @Test
    fun opensGamesHubWithoutPaywall() {
        composeRule.onNodeWithTag("desktop-hub-entertainment").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub-app-games").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Games", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("World Adventure", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText("Pro", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty() ||
                composeRule.onAllNodesWithText("$14.99", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isEmpty(),
        )
        composeRule.onNodeWithText("返回桌面", useUnmergedTree = true).performClick()
    }

    @Test
    fun presetsRootInPhone() {
        composeRule.onNodeWithTag("desktop-hub-social_hub").performClick()
        composeRule.onNodeWithTag("hub-app-phone").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Phone", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Root", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun opensMessengerFromDockAndReturns() {
        composeRule.onNodeWithTag("desktop-dock-messenger").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Messenger", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("返回桌面", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
    }

    @Test
    fun opensImagingStudioFromDockAndSwitchesBackends() {
        composeRule.onNodeWithTag("desktop-dock-imaging").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("imaging-studio").assertIsDisplayed()
        composeRule.onNodeWithText("Imaging Studio", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("imaging-backend-on_device").assertIsDisplayed()
        composeRule.onNodeWithTag("imaging-on-device-card").assertIsDisplayed()
        composeRule.onNodeWithText("None selected", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("imaging-backend-forge").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("imaging-test-connection").assertIsDisplayed()
        composeRule.onNodeWithText("AUTOMATIC1111", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()

        composeRule.onNodeWithTag("imaging-backend-local_dream").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("imaging-backend-local_dream").assertIsDisplayed()
        composeRule.onNodeWithTag("imaging-test-connection").assertIsDisplayed()
        composeRule.onNodeWithTag("imaging-generate").assertIsDisplayed()
        composeRule.onNodeWithTag("imaging-prompt").assertIsDisplayed()

        composeRule.onNodeWithTag("imaging-back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
    }
}
