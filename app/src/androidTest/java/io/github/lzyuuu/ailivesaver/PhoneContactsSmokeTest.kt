package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneContactsSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()
    private var rootId = 0L

    @Before
    fun seedDesktopShell() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        writeWelcomeGuideCompleted(context, true)
        WorldStore(context).use { store ->
            DesktopSeed.ensureDesktopWorld(store, "焰宇", "smoke", context)
            rootId = store.characters().first { it.name == "Root" }.id
        }
        seedInstallOnHomeForSmoke(context, listOf("phone"))
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun phoneContactDialsIntoMatchingMessengerConversation() {
        openDesktopAppFromGrid(composeRule, "phone")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("通话", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("联系人", useUnmergedTree = true).assertIsDisplayed()
        // 联系人行以稳定 tag 定位，并在其子树内校验角色名（避免整屏 Root 文本唯一性断言）。
        composeRule.onNode(
            hasTestTag("phone-contact-$rootId") and hasAnyDescendant(hasText("Root")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("phone-contact-$rootId", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        val listVisible = composeRule.onAllNodesWithTag("messenger-list").fetchSemanticsNodes().isNotEmpty()
        if (listVisible) {
            // 列表行同样以 tag 定位（chat-character-$id），不依赖 Root 文本唯一。
            composeRule.onNodeWithTag("chat-character-$rootId", useUnmergedTree = true).performClick()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("messenger-conversation", useUnmergedTree = true).assertIsDisplayed()
        // MS-02 空态大头像卡：稳定 tag + 子树内角色名断言。
        // 顶栏与空态卡各有一处 Root 文本，因此不用整屏 onNodeWithText("Root") 唯一断言。
        composeRule.onNode(
            hasTestTag("messenger-empty-card") and hasAnyDescendant(hasText("Root")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }
}
