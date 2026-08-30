package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * MS-02 空态大头像卡（参考 ref-11-chat-detail）的真实设备验证：
 * 真实空会话显示卡片且 composer 可用；有消息的会话不显示卡片。
 */
@RunWith(AndroidJUnit4::class)
class MessengerEmptyCardSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedDesktopShell() {
        seedDesktopShellForSmoke(InstrumentationRegistry.getInstrumentation().targetContext)
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    private fun deleteCharacter(id: Long) {
        WorldStore(InstrumentationRegistry.getInstrumentation().targetContext).use { store ->
            store.writableDatabase.execSQL(
                "UPDATE characters SET active = 0 WHERE id = ?",
                arrayOf(id),
            )
            store.deleteCharacter(id)
        }
    }

    @Test
    fun emptyConversationShowsAvatarCardAndKeepsComposerUsable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "Empty ${System.nanoTime()}"
        val id = WorldStore(context).use {
            it.addCharacter(name, "quiet poet", "resident", "", "", "")
        }
        try {
            composeRule.onNodeWithTag("desktop-dock-messenger").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("messenger-search").performTextInput(name)
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("chat-character-$id", useUnmergedTree = true).performClick()
            composeRule.waitForIdle()

            // 空态卡：大头像 + 角色名（顶栏与卡片各一处）+ 开始聊天提示。
            composeRule.onNodeWithTag("messenger-empty-card").assertIsDisplayed()
            composeRule.onAllNodesWithText(name, useUnmergedTree = true).assertCountEquals(2)
            composeRule.onNodeWithText("说点什么来开始。", useUnmergedTree = true).assertIsDisplayed()

            // composer 保留可用：能输入、输入后发送按钮可用。
            composeRule.onNodeWithTag("messenger-send").assertIsDisplayed()
            composeRule.onNode(hasSetTextAction()).performTextInput("hello")
            composeRule.onNodeWithTag("messenger-send").assertIsEnabled()
        } finally {
            deleteCharacter(id)
        }
    }

    @Test
    fun conversationWithMessagesHidesAvatarCard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "Busy ${System.nanoTime()}"
        val id = WorldStore(context).use { store ->
            store.addCharacter(name, "talkative", "resident", "", "", "").also { charId ->
                store.addMessage(charId, "assistant", "很高兴见到你")
            }
        }
        try {
            composeRule.onNodeWithTag("desktop-dock-messenger").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("messenger-search").performTextInput(name)
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("chat-character-$id", useUnmergedTree = true).performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("messenger-empty-card").assertDoesNotExist()
            composeRule.onNodeWithText("很高兴见到你", useUnmergedTree = true).assertIsDisplayed()
            composeRule.onNodeWithTag("messenger-send").assertIsDisplayed()
        } finally {
            deleteCharacter(id)
        }
    }
}
