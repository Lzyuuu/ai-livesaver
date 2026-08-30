package io.github.lzyuuu.ailivesaver

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoreGatingAndDockTest {
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
        // 门控用例需要干净的安装状态：清空 app_install
        WorldStore(context).use { store ->
            store.writableDatabase.delete("app_install", null, null)
            DesktopSeed.ensureDesktopWorld(store, userName = "焰宇", about = "gating", context = context)
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun uninstalledAppIsGatedToStoreFromGrid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        seedHomeGridForSmoke(context, listOf("ustagram"), installed = false)
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        swipeDesktopToAppsPage(composeRule)
        composeRule.onNodeWithTag("desktop-grid-ustagram").performClick()
        composeRule.waitForIdle()
        // 商店屏出现而非 Ustagram 屏
        composeRule.onNodeWithTag("store-seg-store").assertIsDisplayed()
        composeRule.onNodeWithTag("store-list").performScrollToNode(hasTestTag("store-tile-ustagram"))
        composeRule.onNodeWithTag("store-tile-ustagram").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("ustagram-screen", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun installedAppOpensNormallyFromGrid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        seedStoreInstallForSmoke(context, setOf("ustagram"))
        seedHomeDesktopAppsForSmoke(context, setOf("ustagram"))
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        swipeDesktopToAppsPage(composeRule)
        composeRule.onNodeWithTag("desktop-grid-ustagram").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ustagram-screen", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun deepLinkToUninstalledAppDegradesToStoreWithoutCrash() {
        // 深链打开未安装的 Y：降级到商店而非崩溃。
        // 用 launchActivityForResult 携带 extra 重启 Activity，等价于系统冷启动深链。
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.activityRule.scenario.close()
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(OPEN_DESKTOP_APP_EXTRA, "y")
        androidx.test.core.app.ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { }
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithTag("store-seg-store", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("store-seg-store").assertIsDisplayed()
        }
    }

    @Test
    fun dockHasFixedStoreSlot() {
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-dock-store").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-dock-messenger").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-dock-imaging").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-dock-gallery").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-dock-settings").assertIsDisplayed()
    }

    @Test
    fun storeOpensFromDockAndBackToDesktop() {
        composeRule.onNodeWithTag("desktop-dock-store").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store-seg-store").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-back-bar").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
    }

    @Test
    fun addedHomeAppAppearsOnDockAndDisappearsWhenRemoved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = System.currentTimeMillis()
        WorldStore(context).use { store ->
            store.writableDatabase.delete("app_install", null, null)
            store.saveAppInstall(
                PersistedAppInstall("ustagram", InstallStatus.INSTALLED, now, true, 1, "1.0", now),
            )
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-dock-store").assertIsDisplayed()
        // V4.51 Dock 固定五入口；on_home 应用落在首页网格而非 Dock。
        swipeDesktopToAppsPage(composeRule)
        composeRule.onNodeWithTag("desktop-grid-ustagram").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-grid-ustagram").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ustagram-screen", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("ustagram-back", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        WorldStore(context).use { store ->
            store.saveAppInstall(
                PersistedAppInstall("ustagram", InstallStatus.INSTALLED, now, false, 0, "1.0", now),
            )
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-dock-store").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("desktop-dock-ustagram", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun upcomingPackageIsGatedEvenWhenNotInstalled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WorldStore(context).use { store ->
            store.writableDatabase.delete("app_install", null, null)
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("desktop-dock-store").performClick()
        composeRule.waitForIdle()
        // V4.51 目录：upcoming 包 face-enhance 不可安装，详情只有"即将推出"。
        composeRule.onNodeWithTag("store-list").performScrollToNode(hasTestTag("store-tile-face-enhance"))
        composeRule.onNodeWithTag("store-tile-face-enhance").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store-detail-upcoming", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("store-detail-get", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }
}
