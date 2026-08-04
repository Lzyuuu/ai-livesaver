package io.github.lzyuuu.ailivesaver

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation
                .executeShellCommand("pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
                .close()
        }
        writeWelcomeGuideCompleted(context, true)
        resetImagingStudioOnDeviceForSmoke(context)
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
        composeRule.onNodeWithTag("ustagram-screen", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("ustagram-back", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("desktop-hub-sheet", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun opensGamesHubWithoutPaywall() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithTag("desktop-hub-entertainment").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub-app-games").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("games-hub").assertIsDisplayed()
        composeRule.onNodeWithText("Games Hub", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Interactive Experiences", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("World Adventure", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Choose-your-own-adventure story.", useUnmergedTree = true)
            .assertIsDisplayed()
        assertNoPaywall()
        composeRule.onNodeWithTag("games-entry-world_adventure").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("game-placeholder-world_adventure").assertIsDisplayed()
        composeRule.onNodeWithText("World Adventure", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.game_placeholder_status),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-back-bar", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("games-hub").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.desktop_back_to_home),
            useUnmergedTree = true,
        ).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("desktop-hub-sheet", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun opensAllSixGamePlaceholdersWithoutPaywall() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithTag("desktop-hub-entertainment").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub-app-games").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("games-hub").assertIsDisplayed()
        assertNoPaywall()
        GamesHubEntries.forEach { game ->
            composeRule.onNodeWithTag("games-entry-${game.id}").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("game-placeholder-${game.id}").assertIsDisplayed()
            composeRule.onNodeWithText(
                context.getString(game.titleRes),
                useUnmergedTree = true,
            ).assertIsDisplayed()
            composeRule.onNodeWithText(
                context.getString(R.string.game_placeholder_status),
                useUnmergedTree = true,
            ).assertIsDisplayed()
            assertNoPaywall()
            composeRule.onNodeWithTag("desktop-back-bar", useUnmergedTree = true).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("games-hub").assertIsDisplayed()
        }
    }

    private fun assertNoPaywall() {
        assertTrue(
            composeRule.onAllNodesWithText("Pro", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
        assertTrue(
            composeRule.onAllNodesWithText("$14.99", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun presetsRootInPhone() {
        composeRule.onNodeWithTag("desktop-hub-social_hub").performClick()
        composeRule.onNodeWithTag("hub-app-phone").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("desktop-back-bar").assertIsDisplayed()
        composeRule.onNodeWithText("Phone", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Root", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun opensMessengerFromDockAndReturns() {
        composeRule.onNodeWithTag("desktop-dock-messenger").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Messenger", useUnmergedTree = true).assertIsDisplayed()
        // Messenger exposes the desktop action as an accessibility description on its icon.
        // Assert the user-visible external contract instead of requiring an implementation label.
        composeRule.onNodeWithContentDescription("返回桌面", useUnmergedTree = true).performClick()
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
        composeRule.onNodeWithTag("imaging-prompt").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("imaging-generate").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("imaging-back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
    }
}
