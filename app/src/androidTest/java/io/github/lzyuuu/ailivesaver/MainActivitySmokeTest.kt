package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun exposesSocialWorldDestinations() {
        listOf("World", "Chats", "Moments", "Commons", "Me").forEach { label ->
            composeRule.onAllNodesWithText(label, useUnmergedTree = true)
                .get(0)
                .assertIsDisplayed()
        }
    }

    @Test
    fun exposesWorldChronicleEntry() {
        composeRule.onNode(hasScrollToIndexAction(), useUnmergedTree = true)
            .performScrollToIndex(7)
        composeRule.onNodeWithText("世界纪事", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("全部已读", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun opensFullWorldChronicle() {
        composeRule.onNode(hasScrollToIndexAction(), useUnmergedTree = true)
            .performScrollToIndex(7)
        composeRule.onNodeWithText("查看全部", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(
            "这里保留世界最近发生的变化，不会因为已读而消失。",
            useUnmergedTree = true,
        ).get(0)
    }

    @Test
    fun opensUpdateScreenFromMe() {
        composeRule.onNodeWithText("Me", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasScrollToIndexAction(), useUnmergedTree = true)
            .performScrollToIndex(13)
        composeRule.onNodeWithText("关于与更新", useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithText("检查更新", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun exposesExplicitProviderFallbackConfiguration() {
        composeRule.onNodeWithText("Me", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasScrollToIndexAction(), useUnmergedTree = true)
            .performScrollToIndex(6)
        composeRule.onNodeWithText("推理配置", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("独立视觉 Provider", useUnmergedTree = true)
            .performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("备用 Provider", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun exposesThemeChoiceInMe() {
        composeRule.onNodeWithText("Me", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("外观", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("跟随系统", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("深色", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("浅色", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("跟随系统", useUnmergedTree = true).performClick()
    }

    @Test
    fun persistsContextBudgetPerInferenceProfile() {
        val store = ProviderStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val config = ProviderConfig(
            baseUrl = "https://example.com/v1",
            model = "test-model",
            apiKey = "test-key",
            contextBudget = 32_768,
        )

        try {
            store.saveTask(ProviderTask.Vision, config)
            assertEquals(32_768, store.loadTask(ProviderTask.Vision)?.contextBudget)
        } finally {
            store.clearTask(ProviderTask.Vision)
        }
    }
}
