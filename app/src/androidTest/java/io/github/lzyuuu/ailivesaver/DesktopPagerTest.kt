package io.github.lzyuuu.ailivesaver

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DT-02 桌面分页行为：两页 HorizontalPager（主页 + 全部应用网格）、
 * 固定 Dock 与页码指示器两页可见、返回桌面恒定落回第一页。
 */
@RunWith(AndroidJUnit4::class)
class DesktopPagerTest {
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
                about = "desktop-pager",
                context = context,
            )
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun homeElementsOnlyExistOnFirstPage() {
        // 第一页：主页元素 + Dock + 页码指示器；第二页网格尚未组合。
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-pager").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-page-home").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-root-card").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-dock").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-page-indicator").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("desktop-page-apps", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
        assertTrue(
            composeRule.onAllNodesWithTag("desktop-app-grid", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun swipeLeftRevealsFullAppGridAndDockStaysVisible() {
        swipeDesktopToAppsPage(composeRule)
        // 第二页：完整应用网格；Dock 与页码指示器固定可见；主页元素已离开组合。
        composeRule.onNodeWithTag("desktop-page-apps").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-app-grid").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-grid-characters").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-dock").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-dock-messenger").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-page-indicator").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("desktop-root-card", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
        assertTrue(
            composeRule.onAllNodesWithText("FANCY OS · 开放入口", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun pageIndicatorDotNavigatesToAppsPage() {
        // 页码指示器可用：点击第二颗圆点翻到第二页。
        composeRule.onNodeWithTag("desktop-page-dot-1").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("desktop-page-apps").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("desktop-page-apps").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-dock").assertIsDisplayed()
    }

    @Test
    fun swipeRightReturnsToHomePage() {
        swipeDesktopToAppsPage(composeRule)
        composeRule.onNodeWithTag("desktop-page-apps").assertIsDisplayed()
        swipeDesktopToHomePage(composeRule)
        composeRule.onNodeWithTag("desktop-page-home").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-root-card").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-dock").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("desktop-page-apps", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun returningToDesktopLandsBackOnHomePage() {
        // 从第二页进入应用再返回：新的 SystemDesktopScreen 组合恒定回到第一页。
        swipeDesktopToAppsPage(composeRule)
        composeRule.onNodeWithTag("desktop-grid-characters").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("characters-app").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回桌面", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-page-home").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-root-card").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("desktop-page-apps", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun appsPageContainsAllReachableApps() {
        // 已安装但未上首页的目录 App 也在第二页（desktop-apps-* 补充项），
        // 未安装的门控目录 App 不出现。
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        seedStoreInstallForSmoke(context, setOf("ustagram"))
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        swipeDesktopToAppsPage(composeRule)
        // 首页网格项（Characters）保留 desktop-grid-* 标签。
        composeRule.onNodeWithTag("desktop-grid-characters").assertIsDisplayed()
        // 常开壳层入口。
        composeRule.onNodeWithTag("desktop-apps-messenger").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-apps-imaging").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-apps-gallery").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-apps-settings").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-apps-store").assertIsDisplayed()
        // 已安装未上首页：可达，出现在完整网格。
        composeRule.onNodeWithTag("desktop-apps-ustagram").assertIsDisplayed()
        // 未安装的门控目录 App 不可达，不出现。
        assertTrue(
            composeRule.onAllNodesWithTag("desktop-apps-rebbit", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
        assertTrue(
            composeRule.onAllNodesWithTag("desktop-grid-rebbit", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }
}
