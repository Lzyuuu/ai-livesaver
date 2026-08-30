package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * SO-11 存储文件式布局冒烟：视觉结构（摘要卡 + storage-browser + storage-entry-* 层级导航）
 * 与双入口（设置索引 me-setting-storage、桌面 SystemCore hub → Storage 直达真实页）。
 */
@RunWith(AndroidJUnit4::class)
class StorageBrowserSmokeTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(SeededMainActivityRule())
        .around(composeRule)

    @Before
    fun seedSandboxFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // 在应用沙盒内种子确定性层级：so11_seed_dir/seed_note.txt。
        val dir = File(context.filesDir, "so11_seed_dir")
        dir.mkdirs()
        File(dir, "seed_note.txt").writeText("so11")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
    }

    @Test
    fun settingsEntryShowsFileBrowserStructure() {
        composeRule.onNodeWithTag("desktop-dock-settings").performClick()
        composeRule.onNodeWithTag("me-settings-list")
            .performScrollToNode(hasTestTag("me-setting-storage"))
        composeRule.onNodeWithTag("me-setting-storage").assertIsDisplayed().performClick()
        waitForTag("storage-settings-screen")
        composeRule.onNodeWithTag("storage-settings-screen", useUnmergedTree = true).assertIsDisplayed()

        // 视觉结构：面包屑路径 + 四行摘要卡 + 文件浏览区。
        composeRule.onNodeWithTag("storage-path", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("storage-summary", useUnmergedTree = true).assertIsDisplayed()
        waitForTag("storage-browser")
        composeRule.onNodeWithTag("storage-browser", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("placeholder-app", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )

        // 进入种子目录：面包屑更新，文件行可见；返回先回到根，再回到设置索引。
        composeRule.onNodeWithTag("storage-settings-screen", useUnmergedTree = true)
            .performScrollToNode(hasTestTag("storage-entry-so11_seed_dir"))
        composeRule.onNodeWithTag("storage-entry-so11_seed_dir", useUnmergedTree = true)
            .assertIsDisplayed().performClick()
        waitForTag("storage-entry-so11_seed_dir/seed_note.txt")
        composeRule.onNodeWithText("/so11_seed_dir", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("storage-entry-so11_seed_dir/seed_note.txt", useUnmergedTree = true)
            .assertIsDisplayed()

        composeRule.onNodeWithText("返回", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("/", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("返回", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("me-settings-list").assertIsDisplayed()
    }

    @Test
    fun desktopEntryOpensRealStorageScreenAndBackReturnsHome() {
        // 桌面入口 = 首页应用网格瓦片；Storage 受商店安装态门控，先安装并添加到首页。
        seedInstallOnHomeForSmoke(
            InstrumentationRegistry.getInstrumentation().targetContext,
            listOf("storage"),
        )
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
        swipeDesktopToAppsPage(composeRule)
        composeRule.onNodeWithTag("desktop-app-grid")
            .performScrollToNode(hasTestTag("desktop-grid-storage"))
        composeRule.onNodeWithTag("desktop-grid-storage").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        waitForTag("storage-settings-screen")

        // 直达真实 StorageScreen：不再有占位页与「打开存储详情」跳转按钮。
        composeRule.onNodeWithTag("storage-settings-screen", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("placeholder-app", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            composeRule.onAllNodesWithText("打开存储详情", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        waitForTag("storage-browser")

        // 返回到系统桌面。
        composeRule.onNodeWithText("返回", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
