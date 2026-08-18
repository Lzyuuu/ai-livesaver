package io.github.lzyuuu.ailivesaver

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesktopAppLongPressTest {
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
                about = "desktop-long-press",
                context = context,
            )
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun charactersAndDockDoNotExposeUninstallMenu() {
        composeRule.onNodeWithTag("desktop-grid-characters")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithTag("desktop-grid-menu-characters", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )

        assertTrue(
            composeRule.onAllNodesWithTag("desktop-grid-menu-messenger", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun longPressMenuOpensInstalledHomeApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        seedHomeDesktopAppsForSmoke(context, setOf("rebbit"))
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("system-desktop").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("desktop-grid-rebbit")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("desktop-grid-menu-rebbit", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-grid-menu-open-rebbit", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rebbit-screen").assertIsDisplayed()
    }

    @Test
    fun removeFromHomeKeepsInstallAndAllowsReAddFromLibrary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        seedHomeDesktopAppsForSmoke(context, setOf("y"))
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("system-desktop").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("desktop-grid-y")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("desktop-grid-menu-remove-y", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        WorldStore(context).use { store ->
            val install = store.loadAppInstall("y")
            assertNotNull(install)
            assertEquals(InstallStatus.INSTALLED, install!!.status)
            assertFalse(install.onHome)
        }
        assertTrue(
            composeRule.onAllNodesWithTag("desktop-grid-y", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )

        composeRule.onNodeWithTag("desktop-dock-store").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store-seg-lib").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store-row-y-open").assertIsDisplayed()
        composeRule.onNodeWithTag("store-row-y").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store-detail-add-home").performClick()
        composeRule.waitForIdle()

        WorldStore(context).use { store ->
            val install = store.loadAppInstall("y")
            assertNotNull(install)
            assertTrue(install!!.onHome)
        }
        composeRule.onNodeWithTag("desktop-back-bar").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("desktop-grid-y").assertIsDisplayed()
    }

    @Test
    fun uninstallShowsConfirmationAndRestoresStoreGet() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        seedHomeDesktopAppsForSmoke(context, setOf("ustagram"))
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("desktop-grid-ustagram")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("desktop-grid-menu-uninstall-ustagram", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("desktop-grid-uninstall-dialog-ustagram", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-grid-uninstall-dialog-ustagram", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-grid-uninstall-confirm-ustagram", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-grid-uninstall-confirm-ustagram", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        WorldStore(context).use { store ->
            assertNull(store.loadAppInstall("ustagram"))
        }
        assertTrue(
            composeRule.onAllNodesWithTag("desktop-grid-ustagram", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )

        composeRule.onNodeWithTag("desktop-dock-store").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store-row-ustagram-get").assertIsDisplayed()
    }
}
