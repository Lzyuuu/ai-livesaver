package io.github.lzyuuu.ailivesaver

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

/**
 * Games Hub 冒烟：通过 testTag 从桌面网格进入，验证六个入口与
 * World Adventure 可玩会话的关键控件（规则/开始/选项/结果）与核心动作。
 */
@RunWith(AndroidJUnit4::class)
class GamesHubSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedDesktopShell() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        writeWelcomeGuideCompleted(context, true)
        WorldStore(context).use { store ->
            DesktopSeed.ensureDesktopWorld(store, "焰宇", "smoke", context)
        }
        seedInstallOnHomeForSmoke(context, listOf("games"))
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun hubOpensFromGridWithAllSixEntries() {
        swipeDesktopToAppsPage(composeRule)
        composeRule.onNodeWithTag("desktop-grid-games").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("games-hub").assertIsDisplayed()
        composeRule.onNodeWithText("Games Hub", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Interactive Experiences", useUnmergedTree = true).assertIsDisplayed()
        GamesHubEntries.forEach { game ->
            composeRule.onNodeWithTag("games-entry-${game.id}")
                .performScrollTo()
                .assertIsDisplayed()
        }
        assertNoPaywall()
    }

    @Test
    fun worldAdventureSessionCompletesCoreAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        swipeDesktopToAppsPage(composeRule)
        composeRule.onNodeWithTag("desktop-grid-games").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("games-hub").assertIsDisplayed()
        assertNoPaywall()

        composeRule.onNodeWithTag("games-entry-world_adventure").performClick()
        composeRule.waitForIdle()
        // 可玩会话：专属标题 + 规则 + 开始按钮
        composeRule.onNodeWithTag("game-session-world_adventure").assertIsDisplayed()
        composeRule.onNodeWithText("World Adventure", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("game-session-rules-world_adventure").assertIsDisplayed()
        composeRule.onNodeWithTag("game-session-start-world_adventure").performClick()
        composeRule.waitForIdle()
        // 核心动作：选择选项 -> 结果状态
        composeRule.onNodeWithTag("game-option-world_adventure-forest")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("game-session-result-world_adventure")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.game_world_adventure_outcome_forest_detail),
            useUnmergedTree = true,
        ).performScrollTo().assertIsDisplayed()
        assertNoPaywall()

        // 返回 Hub 与桌面
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.desktop_back_to_home),
            useUnmergedTree = true,
        ).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("games-hub").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.desktop_back_to_home),
            useUnmergedTree = true,
        ).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
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
}