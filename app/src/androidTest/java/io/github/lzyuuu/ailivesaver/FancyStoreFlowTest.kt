package io.github.lzyuuu.ailivesaver

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FancyStoreFlowTest {
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
        // 每个用例独立安装状态：清空 app_install 与首访标记，避免用例间泄漏
        WorldStore(context).use { store ->
            store.writableDatabase.delete("app_install", null, null)
            DesktopSeed.ensureDesktopWorld(store, userName = "焰宇", about = "store", context = context)
        }
        context.getSharedPreferences(APP_PREFERENCES, android.content.Context.MODE_PRIVATE)
            .edit().remove("store_first_visit_seen").apply()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    private fun openStoreFromDock() {
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-dock").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop-dock-store").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store-seg-store").assertIsDisplayed()
    }

    @Test
    fun storeShowsSegmentsSearchAndThreeSections() {
        openStoreFromDock()
        composeRule.onNodeWithTag("store-seg-lib").assertIsDisplayed()
        composeRule.onNodeWithTag("store-search").assertIsDisplayed()
        // V4.51：精选位 + 发现网格（store-tile-*）；games 已可用。
        composeRule.onNodeWithTag("store-featured").assertIsDisplayed()
        composeRule.onNodeWithTag("store-list").performScrollToNode(hasTestTag("store-tile-y"))
        composeRule.onNodeWithTag("store-tile-y").assertIsDisplayed()
        composeRule.onNodeWithTag("store-list").performScrollToNode(hasTestTag("store-tile-games"))
        composeRule.onNodeWithTag("store-tile-games").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("store-tile-games", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        composeRule.onNodeWithTag("store-list").performScrollToNode(hasTestTag("store-tile-face-enhance"))
        composeRule.onNodeWithTag("store-tile-face-enhance").assertIsDisplayed()
    }

    @Test
    fun searchFiltersProducts() {
        openStoreFromDock()
        composeRule.onNodeWithTag("store-search").performTextInput("Rebbit")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store-list").performScrollToNode(hasTestTag("store-tile-rebbit"))
        composeRule.onNodeWithTag("store-tile-rebbit").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("store-tile-y", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun emptyLibraryShowsFirstVisitGuideAndSwitchesToStore() {
        openStoreFromDock()
        composeRule.onNodeWithTag("store-seg-lib").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store-lib-empty-guide").assertIsDisplayed()
        composeRule.onNodeWithTag("store-lib-go-browse").performClick()
        composeRule.waitForIdle()
        // 切回 Store 段，且引导已标记看过
        composeRule.onNodeWithTag("store-seg-store").assertIsDisplayed()
        composeRule.onNodeWithTag("store-seg-lib").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store-lib-empty").assertIsDisplayed()
    }

    @Test
    fun installOpenAddHomeAndUninstallFlow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        openStoreFromDock()
        // 网格进入详情：获取 → 已安装（纯应用无下载资产，即时安装）
        composeRule.onNodeWithTag("store-list").performScrollToNode(hasTestTag("store-tile-ustagram"))
        composeRule.onNodeWithTag("store-tile-ustagram").performClick()
        composeRule.waitForIdle()
        // 整页详情：store-detail-page 替换商店列表，不再是叠加的底部弹层
        composeRule.onNodeWithTag("store-detail-page").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("store-detail-sheet", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            composeRule.onAllNodesWithTag("store-list", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        composeRule.onNodeWithTag("store-detail-get").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("store-detail-open", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("store-detail-open").assertIsDisplayed()
        // 添加首页
        composeRule.onNodeWithTag("store-detail-add-home").performClick()
        composeRule.waitForIdle()
        // 持久化断言：数据库里是 INSTALLED + on_home
        WorldStore(context).use { store ->
            val install = store.loadAppInstall("ustagram")!!
            assertTrue(install.status == InstallStatus.INSTALLED)
            assertTrue(install.onHome)
        }
        // 上主页后操作区切换为「打开 + 从主页移除」（对齐参考 ref-62/ref-64）
        composeRule.onNodeWithTag("store-detail-remove-home").assertIsDisplayed()
        // 卸载：详情页停留，状态机回落为「获取」
        composeRule.onNodeWithTag("store-detail-uninstall").performClick()
        composeRule.waitForIdle()
        WorldStore(context).use { store ->
            assertTrue(store.loadAppInstall("ustagram") == null)
        }
        composeRule.onNodeWithTag("store-detail-get").assertIsDisplayed()
        // 详情返回键只回商店列表
        composeRule.onNodeWithTag("store-detail-back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store-list").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("store-detail-page", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun detailSystemBackReturnsToStoreListThenDesktop() {
        openStoreFromDock()
        composeRule.onNodeWithTag("store-list").performScrollToNode(hasTestTag("store-tile-ustagram"))
        composeRule.onNodeWithTag("store-tile-ustagram").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store-detail-page").assertIsDisplayed()
        // 系统返回键：详情 → 商店列表（不退出商店）
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store-list").assertIsDisplayed()
        composeRule.onNodeWithTag("store-seg-store").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("store-detail-page", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        // 再按一次系统返回键：商店 → 桌面（宿主返回栈不受影响）
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
    }

    @Test
    fun upcomingPackageCannotInstall() {
        openStoreFromDock()
        // V4.51：games 已可用；gating 语义由 upcoming 包（face-enhance）承担。
        composeRule.onNodeWithTag("store-list").performScrollToNode(hasTestTag("store-tile-face-enhance"))
        composeRule.onNodeWithTag("store-tile-face-enhance").performClick()
        composeRule.waitForIdle()
        // 整页详情：列表退出组合，详情页独占屏幕
        composeRule.onNodeWithTag("store-detail-page").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("store-detail-sheet", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            composeRule.onAllNodesWithTag("store-list", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        // 详情内没有获取按钮，只有"即将推出"文案
        composeRule.onNodeWithTag("store-detail-upcoming", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("store-detail-get", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }
}
