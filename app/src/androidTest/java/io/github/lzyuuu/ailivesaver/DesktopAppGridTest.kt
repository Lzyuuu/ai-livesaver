package io.github.lzyuuu.ailivesaver

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesktopAppGridTest {
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
            store.writableDatabase.delete("app_install", null, null)
            DesktopSeed.ensureDesktopWorld(
                store,
                userName = "焰宇",
                about = "desktop-grid",
                context = context,
            )
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun rendersCharactersFirstAndHidesQuickEntryHubs() {
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
        swipeDesktopToAppsPage(composeRule)
        composeRule.onNodeWithTag("desktop-app-grid").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-grid-characters").assertIsDisplayed()
        composeRule.onNodeWithText("Characters", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("desktop-hub-social_hub", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
        assertTrue(
            composeRule.onAllNodesWithText("快速入口", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun showsInstalledHomeAppsInJoinOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = System.currentTimeMillis()
        WorldStore(context).use { store ->
            store.saveAppInstall(
                PersistedAppInstall("rebbit", InstallStatus.INSTALLED, now, true, 1, "1.0", now),
            )
            store.saveAppInstall(
                PersistedAppInstall("ustagram", InstallStatus.INSTALLED, now + 1, true, 2, "1.0", now + 1),
            )
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        swipeDesktopToAppsPage(composeRule)
        composeRule.onNodeWithTag("desktop-grid-characters").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-grid-rebbit").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-grid-ustagram").assertIsDisplayed()
    }

    @Test
    fun syncsGridWhenHomeInstallStateChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = System.currentTimeMillis()
        WorldStore(context).use { store ->
            store.saveAppInstall(
                PersistedAppInstall("y", InstallStatus.INSTALLED, now, true, 1, "1.0", now),
            )
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        swipeDesktopToAppsPage(composeRule)
        composeRule.onNodeWithTag("desktop-grid-y").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-grid-y").assertIsDisplayed()

        WorldStore(context).use { store ->
            store.saveAppInstall(
                PersistedAppInstall("y", InstallStatus.INSTALLED, now, false, 0, "1.0", now + 1),
            )
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithTag("desktop-grid-y", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun opensCharactersFromGrid() {
        swipeDesktopToAppsPage(composeRule)
        composeRule.onNodeWithTag("desktop-grid-characters").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("characters-app").assertIsDisplayed()
    }
}
