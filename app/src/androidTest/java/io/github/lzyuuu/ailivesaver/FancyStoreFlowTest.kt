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
        // 三区呈现：可获取 / 即将开放 / 即将推出
        composeRule.onNodeWithTag("store-row-y").assertIsDisplayed()
        composeRule.onNodeWithTag("store-list").performScrollToNode(hasTestTag("store-row-games"))
        composeRule.onNodeWithTag("store-row-games-disabled", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("store-row-phone-disabled", useUnmergedTree = true).assertExists()
        // 即将推出的包
        composeRule.onNodeWithTag("store-list").performScrollToNode(hasTestTag("store-row-hd-upscalers"))
        composeRule.onNodeWithTag("store-row-hd-upscalers-disabled", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun searchFiltersProducts() {
        openStoreFromDock()
        composeRule.onNodeWithTag("store-search").performTextInput("Rebbit")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store-row-rebbit").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("store-row-y", useUnmergedTree = true)
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
        // 获取 → 模拟下载 → 已安装
        composeRule.onNodeWithTag("store-row-ustagram-get").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("store-row-ustagram-open", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("store-row-ustagram-open").assertIsDisplayed()
        // 详情弹层 + 添加首页
        composeRule.onNodeWithTag("store-row-ustagram").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store-detail-sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("store-detail-add-home").performClick()
        composeRule.waitForIdle()
        // 持久化断言：数据库里是 INSTALLED + on_home
        WorldStore(context).use { store ->
            val install = store.loadAppInstall("ustagram")!!
            assertTrue(install.status == InstallStatus.INSTALLED)
            assertTrue(install.onHome)
        }
        // 卸载
        composeRule.onNodeWithTag("store-detail-uninstall").performClick()
        composeRule.waitForIdle()
        WorldStore(context).use { store ->
            assertTrue(store.loadAppInstall("ustagram") == null)
        }
    }

    @Test
    fun comingSoonCannotInstall() {
        openStoreFromDock()
        composeRule.onNodeWithTag("store-list").performScrollToNode(hasTestTag("store-row-games"))
        composeRule.onNodeWithTag("store-row-games-disabled", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("store-row-games").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store-detail-sheet").assertIsDisplayed()
        // 详情内没有获取按钮，只有"即将开放"文案
        assertTrue(
            composeRule.onAllNodesWithTag("store-detail-get", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }
}
