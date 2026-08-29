package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 阶段③ Root Producer 冒烟（ref-83 对齐）：页面结构 + 「让 Root 编写方案」
 * 经云端 Chat brain 生成方案并落为曲目卡。Provider 由 instrumentation args 提供。
 */
@RunWith(AndroidJUnit4::class)
class RootProducerSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedDesktopShell() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        writeWelcomeGuideCompleted(context, true)
        WorldStore(context).use { store ->
            DesktopSeed.ensureDesktopWorld(store, "焰宇", "smoke", context)
            seedInstallOnHomeForSmoke(context, listOf("root_producer"))
            // 桌面网格为单页，历史 smoke 累积的图标会把新图标挤出屏幕。
            store.writableDatabase.execSQL("DELETE FROM app_install WHERE app_id != 'root_producer'")
        }
        val args = InstrumentationRegistry.getArguments()
        val baseUrl = args.getString("baseUrl")
        val model = args.getString("model")
        val apiKey = args.getString("apiKey")
        if (baseUrl != null && model != null && apiKey != null) {
            ProviderStore(context).save(
                ProviderConfig(
                    preset = ProviderPreset.Custom,
                    baseUrl = baseUrl,
                    model = model,
                    apiKey = apiKey,
                ),
            )
        }
        // 保证空态起点：清掉历史曲目（结构测试断言空态文案）。
        context.getSharedPreferences("root_producer", android.content.Context.MODE_PRIVATE)
            .edit().remove("tracks").apply()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    private fun openProducerPage() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.intent.putExtra("open_desktop_app", "root_producer")
            activity.recreate()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun producerPageStructureMatchesReference() {
        openProducerPage()
        composeRule.onNodeWithText("Root Producer", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("producer-idea").assertIsDisplayed()
        composeRule.onNodeWithTag("producer-plan").assertIsDisplayed()
        composeRule.onNodeWithTag("producer-empty").assertIsDisplayed()
    }

    @Test
    fun planGenerationPersistsTrackCard() {
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(
            "需要 instrumentation args 提供 Provider",
            args.getString("baseUrl") != null,
        )
        openProducerPage()
        composeRule.onNodeWithTag("producer-idea").performTextInput("a dreamy summer night drive")
        composeRule.onNodeWithTag("producer-plan").performClick()
        composeRule.waitUntil(180_000) {
            composeRule.onAllNodesWithTag("producer-empty", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
        // 想法文本同时存在于输入框与曲目卡，用卡片节点本身断言（避免文本二义）。
        composeRule.onAllNodesWithTag("producer-track", useUnmergedTree = true)[0]
            .assertIsDisplayed()
    }
}
