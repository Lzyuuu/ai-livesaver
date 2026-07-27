package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    fun opensUpdateScreenFromMe() {
        composeRule.onNodeWithText("Me", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasScrollToIndexAction(), useUnmergedTree = true)
            .performScrollToIndex(13)
        composeRule.onNodeWithText("关于与更新", useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithText("检查更新", useUnmergedTree = true).assertIsDisplayed()
    }
}
