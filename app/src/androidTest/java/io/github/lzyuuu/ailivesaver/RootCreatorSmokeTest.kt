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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Root Creator（ROOT 造卡）冒烟：通过 testTag 从桌面网格进入，
 * 验证页面结构与核心动作（描述 -> 生成角色卡 -> 保存为角色并落库）。
 */
@RunWith(AndroidJUnit4::class)
class RootCreatorSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedDesktopShell() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        writeWelcomeGuideCompleted(context, true)
        WorldStore(context).use { store ->
            DesktopSeed.ensureDesktopWorld(store, "焰宇", "smoke", context)
        }
        seedInstallOnHomeForSmoke(context, listOf("root_creator"))
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun pageStructureMatchesReference() {
        swipeDesktopToAppsPage(composeRule)
        composeRule.onNodeWithTag("desktop-grid-root_creator").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("root-creator").assertIsDisplayed()
        composeRule.onNodeWithText("Root Creator", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("ROOT 造卡", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("root-creator-input").assertIsDisplayed()
        composeRule.onNodeWithTag("root-creator-generate").assertIsDisplayed()
        composeRule.onNodeWithTag("root-creator-empty").assertIsDisplayed()
    }

    @Test
    fun generateCardFromDescriptionThenSaveAsCharacter() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        swipeDesktopToAppsPage(composeRule)
        composeRule.onNodeWithTag("desktop-grid-root_creator").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("root-creator").assertIsDisplayed()

        // 核心动作：描述 -> 生成草稿卡。
        composeRule.onNodeWithTag("root-creator-input")
            .performTextInput("小满，穿黑色风衣的女法医，冷静毒舌。")
        composeRule.onNodeWithTag("root-creator-generate").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("root-creator-result", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("root-creator-result").assertIsDisplayed()
        composeRule.onNodeWithTag("root-creator-card-json")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("root-creator-save")
            .performScrollTo()
            .assertIsDisplayed()

        // 核心动作：保存为角色 -> 落库 + 界面回到空态。
        composeRule.onNodeWithTag("root-creator-save").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("root-creator-empty", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("root-creator-empty").assertIsDisplayed()
        assertTrue(
            "保存后应存在角色 小满",
            WorldStore(context).use { store ->
                store.characters().any { it.name == "小满" }
            },
        )

        // 返回桌面。
        composeRule.onNodeWithTag("root-creator-back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
    }
}