package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Before
    fun seedDesktopShell() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
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

        val postId = WorldStore(context).use { store ->
            store.posts(Y_POST_KIND).first { it.body == "A short update" }.id
        }

        composeRule.onNodeWithTag("y-reply-input-$postId").performTextInput("Top level reply")
        composeRule.onNodeWithTag("y-reply-send-$postId").performClick()
        val topCommentId = waitForCommentPersisted(postId, "Top level reply")
        assertReplyVisibleOnFeed(postId, "Top level reply")

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
        waitForCommentPersisted(postId, "Nested under top")
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

        val existingResidentPostIds = WorldStore(context).use { store ->
            store.posts(Y_POST_KIND)
                .filter { it.authorKind == "resident" }
                .map { it.id }
                .toSet()
        }

        composeRule.onNodeWithTag("y-generate").performClick()
        var generatedPost: SocialPost? = null
        composeRule.waitUntil(timeoutMillis = 15_000) {
            generatedPost = WorldStore(context).use { store ->
                store.posts(Y_POST_KIND).firstOrNull { post ->
                    post.id !in existingResidentPostIds &&
                        post.authorKind == "resident" &&
                        post.body.isNotBlank() &&
                        post.body != "..."
                }
            }
            generatedPost != null || composeRule.onAllNodesWithTag("y-status", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        generatedPost?.let { post ->
            assertReplyVisibleOnFeed(post.id, post.body)
            assertNotEquals("...", post.body)
            assertTrue(post.body.isNotBlank())
        } ?: composeRule.onNodeWithTag("y-status", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("y-back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
    }

    private fun waitForCommentPersisted(postId: Long, body: String, timeoutMillis: Long = 10_000): Long {
        composeRule.waitUntil(timeoutMillis) {
            WorldStore(context).use { store ->
                store.comments(postId).any { it.body == body }
            }
        }
        return WorldStore(context).use { store ->
            store.comments(postId).first { it.body == body }.id
        }
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
