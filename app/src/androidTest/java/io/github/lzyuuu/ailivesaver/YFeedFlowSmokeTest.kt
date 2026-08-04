package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performTextInput
import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YFeedFlowSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val notificationPermissionRule = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        org.junit.rules.TestRule { base, _ -> base }
    }

    private lateinit var context: android.content.Context
    private var previousWorldProvider: ProviderConfig? = null

    @Before
    fun seedDesktopShell() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val providerStore = ProviderStore(context)
        previousWorldProvider = providerStore.loadTask(ProviderTask.World)
        providerStore.saveTask(
            ProviderTask.World,
            ProviderConfig(
                preset = ProviderPreset.Custom,
                baseUrl = "http://127.0.0.1:9/v1",
                model = "ui-smoke-disabled",
                apiKey = "test-key",
                capabilities = ProviderCapabilities(),
            ),
        )
        writeWelcomeGuideCompleted(context, true)
        WorldStore(context).use { store ->
            DesktopSeed.ensureDesktopWorld(
                store = store,
                userName = "Lai",
                about = "y-flow",
                context = context,
            )
            store.posts(Y_POST_KIND).forEach { post ->
                if (post.authorKind == "user") store.deleteUserPost(post.id)
                else store.deleteAiPost(post.id)
            }
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @After
    fun restoreWorldProvider() {
        val providerStore = ProviderStore(context)
        previousWorldProvider?.let { providerStore.saveTask(ProviderTask.World, it) }
            ?: providerStore.clearTask(ProviderTask.World)
    }

    @Test
    fun opensYComposePostsNestedRepliesGenerateAndReturns() {
        composeRule.onNodeWithTag("desktop-hub-social_hub").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub-app-y").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("y-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("y-disclaimer").assertIsDisplayed()
        composeRule.onNodeWithTag("y-empty").assertIsDisplayed()

        composeRule.onNodeWithTag("y-compose").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("y-compose-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("y-composer-input").performTextInput("A short update")
        composeRule.onNodeWithTag("y-publish").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("y-feed").assertIsDisplayed()
        composeRule.onNodeWithText("A short update", useUnmergedTree = true).assertIsDisplayed()

        val postId = composeRule.onAllNodes(
            SemanticsMatcher("Y feed card tag") { node ->
                node.config.contains(SemanticsProperties.TestTag) &&
                    node.config[SemanticsProperties.TestTag].startsWith("y-feed-card-")
            },
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.TestTag] }
            .first { it.startsWith("y-feed-card-") }
            .removePrefix("y-feed-card-")
            .toLong()

        composeRule.onNodeWithTag("y-reply-input-$postId").performTextInput("Top level reply")
        composeRule.onNodeWithTag("y-reply-send-$postId").performClick()
        assertReplyVisibleOnFeed(postId, "Top level reply")
        val topCommentId = waitForInlineReplyId()

        scrollToFeedCard(postId)
        composeRule.onNodeWithTag("y-inline-reply-$topCommentId", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(
            context.getString(R.string.reply_to_member, "Lai"),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("y-reply-input-$postId").performTextInput("Nested under top")
        composeRule.onNodeWithTag("y-reply-send-$postId").performClick()
        assertReplyVisibleOnFeed(postId, "Nested under top")

        composeRule.onNodeWithTag("y-more").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("y-more-menu").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.y_edit_prompt), useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.save_changes), useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithTag("y-more-menu", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )

        composeRule.onNodeWithTag("y-generate").performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("y-status", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("y-status", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("y-back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
    }

    private fun waitForInlineReplyId(timeoutMillis: Long = 10_000): Long {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodes(
                SemanticsMatcher("Y inline reply tag") { node ->
                    node.config.contains(SemanticsProperties.TestTag) &&
                        node.config[SemanticsProperties.TestTag].startsWith("y-inline-reply-")
                },
                useUnmergedTree = true,
            ).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        return composeRule.onAllNodes(
            SemanticsMatcher("Y inline reply tag") { node ->
                node.config.contains(SemanticsProperties.TestTag) &&
                    node.config[SemanticsProperties.TestTag].startsWith("y-inline-reply-")
            },
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.TestTag] }
            .first()
            .removePrefix("y-inline-reply-")
            .toLong()
    }

    private fun hideSoftKeyboard() {
        composeRule.activityRule.scenario.onActivity { activity ->
            val view = activity.currentFocus ?: activity.window.decorView
            val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
        composeRule.waitForIdle()
    }

    private fun scrollToFeedCard(postId: Long, timeoutMillis: Long = 10_000) {
        val card = composeRule.onNodeWithTag("y-feed-card-$postId", useUnmergedTree = true)
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag("y-feed-card-$postId", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        card.performScrollTo()
    }

    private fun assertReplyVisibleOnFeed(
        postId: Long,
        text: String,
        timeoutMillis: Long = 10_000,
    ) {
        composeRule.waitForIdle()
        hideSoftKeyboard()
        scrollToFeedCard(postId, timeoutMillis)
        composeRule.onNodeWithTag("y-feed-card-$postId", useUnmergedTree = true).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeRule.onNodeWithText(text, substring = true, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }
}
