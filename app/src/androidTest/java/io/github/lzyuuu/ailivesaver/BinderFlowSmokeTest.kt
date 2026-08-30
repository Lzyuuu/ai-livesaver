package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BinderFlowSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedDesktopShell() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        writeWelcomeGuideCompleted(context, true)
        WorldStore(context).use { store ->
            DesktopSeed.ensureDesktopWorld(store, "焰宇", "smoke", context)
            // 隔离：清历史问卷草稿。SO-05 单页会恢复草稿答案，残留的非空 relationship
            // 会让 blankRelationship 校验分支被跳过、改走 Provider 错误文案。
            store.writableDatabase.delete("binder_drafts", null, null)
        }
        seedStoreInstallForSmoke(context, setOf("binder"))
        seedInstallOnHomeForSmoke(context, listOf("binder"))
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    private fun openBinder() {
        swipeDesktopToAppsPage(composeRule)
        composeRule.onNodeWithTag("desktop-grid-binder").performClick()
        composeRule.onNodeWithText("Binder", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun questionnaireShowsAllFieldsAndGenerateOnSingleScrollPage() {
        openBinder()
        composeRule.onNodeWithTag("binder-questionnaire").assertIsDisplayed()
        listOf(
            "relationship",
            "preferences",
            "personality",
            "communication",
            "interests",
            "boundaries",
            "exclusions",
        ).forEach { key ->
            composeRule.onNodeWithTag("binder-field-$key").performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithTag("binder-generate").performScrollTo().assertIsDisplayed()
        // 单页化后不存在分步“下一步”入口。
        composeRule.onNodeWithTag("binder-next").assertDoesNotExist()
    }

    @Test
    fun blankRelationshipShowsErrorAndRegenerateClearsIt() {
        openBinder()
        composeRule.onNodeWithTag("binder-generate").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("binder-error").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("请填写关系偏好").assertIsDisplayed()

        composeRule.onNodeWithTag("binder-field-relationship")
            .performScrollTo()
            .performTextInput("长期陪伴")
        composeRule.onNodeWithTag("binder-generate").performScrollTo().performClick()
        composeRule.waitForIdle()
        // 再次生成会先清空上一次错误；未配置 Provider 时不会出现旧的校验文案。
        composeRule.onNodeWithText("请填写关系偏好").assertDoesNotExist()
    }

    @Test
    fun legacyStepDraftRestoresAnswersWithoutHidingFields() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WorldStore(context).use { store ->
            store.putBinderDraft(
                BinderDraft(
                    id = "binder-default",
                    step = 4,
                    payload = BinderAnswers(relationship = "老友").toJson(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        openBinder()
        composeRule.onNodeWithTag("binder-field-exclusions").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("binder-field-relationship")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("老友")
        composeRule.onNodeWithTag("binder-generate").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun questionnaireNeverExposesRawJsonReview() {
        openBinder()
        composeRule.onNodeWithTag("binder-field-relationship")
            .performScrollTo()
            .performTextInput("长期陪伴")
        composeRule.onNodeWithText("\"relationship\"", substring = true).assertDoesNotExist()
    }
}
