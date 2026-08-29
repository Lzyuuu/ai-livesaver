package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 阶段③ Groups 大厅冒烟（ref-76/76b/76c 对齐）：
 * 1) 空态文案 + 新建弹层结构（创建链路含 IME 交互，已手动实测：建群→落库→入群聊）；
 * 2) seed 直落库的群组在大厅列表显示并可进入群聊。
 */
@RunWith(AndroidJUnit4::class)
class GroupsLobbySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()
    private var rootId = 0L
    private var miraId = 0L

    @Before
    fun seedDesktopShell() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        writeWelcomeGuideCompleted(context, true)
        WorldStore(context).use { store ->
            DesktopSeed.ensureDesktopWorld(store, "焰宇", "smoke", context)
            rootId = store.characters().first { it.name == "Root" }.id
            miraId = store.addCharacter(
                name = "Mira",
                persona = "photographer friend",
                attentionTier = "resident",
                appearance = "",
                clothing = "",
                negativePrompt = "",
                cardJson = "",
            )
            // 保证空态起点：清掉历史群组（含 group: 桥接角色与加入消息）。
            store.writableDatabase.execSQL(
                "DELETE FROM messages WHERE character_id IN (SELECT id FROM characters WHERE card_json LIKE 'group:%')",
            )
            store.writableDatabase.execSQL("DELETE FROM characters WHERE card_json LIKE 'group:%'")
            store.writableDatabase.execSQL("DELETE FROM messenger_group_members")
            store.writableDatabase.execSQL("DELETE FROM messenger_groups")
            seedInstallOnHomeForSmoke(context, listOf("groups"))
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun emptyLobbyShowsReferenceCopyAndSheetStructure() {
        openDesktopAppFromGrid(composeRule, "groups")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("群组", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("FANCY 群组", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("groups-empty").assertIsDisplayed()

        composeRule.onNodeWithTag("groups-new").performClick()
        awaitNode("groups-sheet-title")
        composeRule.onNodeWithTag("groups-new-name").assertIsDisplayed()
        composeRule.onNodeWithTag("groups-new-prompt").assertIsDisplayed()
        // 成员区为 LazyColumn（仅组合可见项，库中可能存在多个历史 Mira 排在屏外）。
        awaitNode("groups-member-$rootId")
        composeRule.onNodeWithTag("groups-member-$rootId").assertIsDisplayed()
        composeRule.onNodeWithTag("groups-member-list").assertIsDisplayed()
        composeRule.onNodeWithTag("groups-create").assertIsDisplayed()
    }

    private fun awaitNode(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun seededGroupListOpensGroupChat() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WorldStore(context).use { store ->
            val persistedId = store.savePersistedMessengerGroup(
                PersistedMessengerGroup(0L, "Weekend Plan", "", listOf(rootId, miraId)),
            )
            store.addCharacter(
                name = "Weekend Plan",
                persona = "Group chat with Root, Mira.",
                attentionTier = "resident",
                appearance = "",
                clothing = "",
                negativePrompt = "",
                cardJson = "group:$persistedId",
            )
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        openDesktopAppFromGrid(composeRule, "groups")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Weekend Plan", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("2 位成员", useUnmergedTree = true).assertIsDisplayed()

        awaitNode("groups-row")
        composeRule.onAllNodesWithTag("groups-row", useUnmergedTree = true)[0].performClick()
        try {
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithTag("messenger-conversation", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                    composeRule.onAllNodesWithTag("messenger-group-conversation", useUnmergedTree = true)
                        .fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            fun exists(tag: String): Boolean =
                composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            val state = buildMap {
                put("groups-lobby", exists("groups-lobby"))
                put("messenger-list", exists("messenger-list"))
                put("messenger-conversation", exists("messenger-conversation"))
                put("messenger-group-conversation", exists("messenger-group-conversation"))
            }
            android.util.Log.e("GroupsTest", "STATE: $state")
        }
        // openMessenger 可能先落 Messenger 列表（requestedChatCharacterId 选中），点群组行进入对话。
        val conversationShown = composeRule.onAllNodesWithTag("messenger-conversation", useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty() ||
            composeRule.onAllNodesWithTag("messenger-group-conversation", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        if (!conversationShown) {
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithTag("messenger-list", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Weekend Plan", useUnmergedTree = true).performClick()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithTag("messenger-group-conversation", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
        composeRule.onNodeWithTag("messenger-group-conversation").assertIsDisplayed()
        composeRule.onNodeWithText("Weekend Plan", useUnmergedTree = true).assertIsDisplayed()
    }
}
