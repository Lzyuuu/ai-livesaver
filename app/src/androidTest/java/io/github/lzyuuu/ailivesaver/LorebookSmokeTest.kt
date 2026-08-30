package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lorebook（世界知识）冒烟：通过 testTag 从桌面网格进入，
 * 验证页面结构与核心动作（书册分组层级 -> 录入事实 -> 保存 -> 列表出现 -> 删除 -> 空态恢复）。
 */
@RunWith(AndroidJUnit4::class)
class LorebookSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedDesktopShell() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        writeWelcomeGuideCompleted(context, true)
        WorldStore(context).use { store ->
            DesktopSeed.ensureDesktopWorld(store, "焰宇", "smoke", context)
            // 确定性空态起点：清掉历史世界事实。
            store.writableDatabase.execSQL("DELETE FROM world_facts")
        }
        seedInstallOnHomeForSmoke(context, listOf("lorebook"))
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun pageStructureMatchesReference() {
        swipeDesktopToAppsPage(composeRule)
        composeRule.onNodeWithTag("desktop-grid-lorebook").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lorebook").assertIsDisplayed()
        composeRule.onNodeWithText("世界书", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("知识", useUnmergedTree = true).assertIsDisplayed()
        // 条目结构（v28）：总开关 + 关键词输入 + 书册选择 + 分组。
        composeRule.onNodeWithTag("lorebook-master-toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("lorebook-keywords").assertIsDisplayed()
        composeRule.onNodeWithTag("lorebook-entry").assertIsDisplayed()
        composeRule.onNodeWithTag("lorebook-save").assertIsDisplayed()
        composeRule.onNodeWithTag("lorebook-book-picker").assertIsDisplayed()
        composeRule.onNodeWithTag("lorebook-group").assertIsDisplayed()
        // SO-12：两本书册分组「已分享」「Root Sudo」及其条目层级（空态）。
        composeRule.onNodeWithTag("lorebook-book-已分享", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("lorebook-book-Root Sudo", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("lorebook-empty").assertIsDisplayed()
    }

    @Test
    fun saveFactThenDeleteRestoresEmptyState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        swipeDesktopToAppsPage(composeRule)
        composeRule.onNodeWithTag("desktop-grid-lorebook").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lorebook").assertIsDisplayed()

        // 核心动作：录入并保存事实（默认书册「已分享」）。
        composeRule.onNodeWithTag("lorebook-entry")
            .performTextInput("这座城市永远下着细雨。")
        composeRule.onNodeWithTag("lorebook-save").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("lorebook-fact", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("lorebook-fact").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("这座城市永远下着细雨。", useUnmergedTree = true)
            .assertIsDisplayed()
        val fact = WorldStore(context).use { it.worldFacts().single() }
        // 默认书册「已分享」、未分组。
        assertEquals(LOREBOOK_SHARED_BOOK, fact.book)
        assertEquals("", fact.group)

        // 删除事实 -> 空态恢复。
        composeRule.onNodeWithTag("lorebook-delete-${fact.id}")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("lorebook-fact", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("lorebook-empty").assertIsDisplayed()
        assertTrue("删除后事实应已落库清除", WorldStore(context).use { it.worldFacts().isEmpty() })

        // 返回桌面。
        composeRule.onNodeWithTag("lorebook-back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
    }

    @Test
    fun rootSudoBookHoldsItsOwnEntryHierarchy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        swipeDesktopToAppsPage(composeRule)
        composeRule.onNodeWithTag("desktop-grid-lorebook").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lorebook").assertIsDisplayed()

        // 书册选择器：切换到「Root Sudo」书册。
        composeRule.onNodeWithTag("lorebook-book-picker").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lorebook-book-option-Root Sudo", useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithTag("lorebook-group").performTextInput("人设")
        composeRule.onNodeWithTag("lorebook-entry")
            .performTextInput("Root Sudo 掌握服务器管理员权限。")
        composeRule.onNodeWithTag("lorebook-save").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("lorebook-fact", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val fact = WorldStore(context).use { it.worldFacts().single() }
        assertEquals(LOREBOOK_ROOT_BOOK, fact.book)
        assertEquals("人设", fact.group)

        // 条目层级：位于「Root Sudo」书册下、分组头内；「已分享」书册保持空态。
        // 书册卡片高于视口，逐元素滚动后再断言可见性。
        composeRule.onNodeWithTag("lorebook-fact", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Root Sudo 掌握服务器管理员权限。", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("lorebook-group-Root Sudo-人设", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("lorebook-empty-已分享", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("lorebook-empty-Root Sudo", useUnmergedTree = true)
            .assertDoesNotExist()
    }
}