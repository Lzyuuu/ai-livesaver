package io.github.lzyuuu.ailivesaver

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmark（性能实验室）冒烟：通过 testTag 从桌面网格进入，
 * 验证页面结构、重复次数选择控件与无模型引导；不依赖下载模型。
 */
@RunWith(AndroidJUnit4::class)
class BenchmarkSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedDesktopShell() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        writeWelcomeGuideCompleted(context, true)
        // 确定性无模型状态：清掉历史激活引擎，避免受联动测试残留影响。
        context.getSharedPreferences("local_engine", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        WorldStore(context).use { store ->
            DesktopSeed.ensureDesktopWorld(store, "焰宇", "smoke", context)
        }
        seedInstallOnHomeForSmoke(context, listOf("benchmark"))
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun pageStructureAndControlsWithoutActiveModel() {
        composeRule.onNodeWithTag("desktop-grid-benchmark").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("benchmark-screen").assertIsDisplayed()
        composeRule.onNodeWithText("性能实验室", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("将运行什么", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("模型", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("设备", useUnmergedTree = true).assertIsDisplayed()

        // 无激活模型：显示引导文案，运行基准按钮存在但禁用（与参考一致）。
        composeRule.onNodeWithText("未找到模型。", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("benchmark-run").assertIsNotEnabled()

        // 核心控件：重复次数 FilterChip 可选。
        listOf("一次", "3×", "5×").forEach { label ->
            composeRule.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
        }
        composeRule.onNodeWithText("5×").performClick()
        composeRule.onNodeWithText("5×").assertIsSelected()

        // 通往模型与引擎入口的按钮。
        composeRule.onNodeWithText("模型与引擎", useUnmergedTree = true)
            .assertIsDisplayed()

        // 返回桌面（标题行返回键为 IconButton，仅 contentDescription 无文本）。
        composeRule.onNodeWithTag("benchmark-back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
    }
}