package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import android.Manifest
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UstagramFlowSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private var previousWorldPreferences: Map<String, Any?> = emptyMap()

    @get:Rule
    val notificationPermissionRule: TestRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        org.junit.rules.TestRule { base, _ -> base }
    }

    @Before
    fun seedUstagramFlow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("world_engine", android.content.Context.MODE_PRIVATE)
        previousWorldPreferences = preferences.all.toMap()
        preferences.edit().putBoolean("enabled", false).commit()
        seedDesktopShellForSmoke(context)
        seedStoreInstallForSmoke(context, setOf("ustagram"))
        clearMomentPostsForSmoke(context)
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @After
    fun cleanupMoments() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        clearMomentPostsForSmoke(context)
        val preferences = context.getSharedPreferences("world_engine", android.content.Context.MODE_PRIVATE)
        val editor = preferences.edit().clear()
        previousWorldPreferences.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        editor.commit()
    }

    @Test
    fun hubEntryComposeLikeReplyGenerateAndReturnToDesktop() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val publishedBody = "Hello from Ustagram smoke"
        val residentIdsBefore = WorldStore(context).use { store ->
            store.postDeletionTargets("moment")
                .filter { it.authorKind != "user" }
                .map { it.id }
                .toSet()
        }

        composeRule.onNodeWithTag("desktop-hub-social_hub").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub-app-ustagram").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ustagram-screen", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("ustagram-compose", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ustagram-composer", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("ustagram-composer-caption", useUnmergedTree = true)
            .performTextInput(publishedBody)
        composeRule.onNodeWithTag("ustagram-attach-ai-image", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("ustagram-composer-post", useUnmergedTree = true).performClick()
        assertFeedTextDisplayed(publishedBody, authorKind = "user")

        val publishedPostId = WorldStore(context).use { store ->
            val post = store.postDeletionTargets("moment")
                .first { it.body == publishedBody && it.authorKind == "user" }
            val feedPost = store.posts("moment").first { it.id == post.id }
            assertFalse(feedPost.reactedByUser)
            assertEquals(0, feedPost.reactionCount)
            post.id
        }

        composeRule.onNodeWithTag("ustagram-post-card", useUnmergedTree = true)
            .performScrollTo()
            .performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            WorldStore(context).use { store ->
                store.posts("moment").any { post ->
                    post.id == publishedPostId && post.reactedByUser && post.reactionCount > 0
                }
            }
        }

        val replyBody = "Nice post"
        composeRule.onNodeWithTag("ustagram-reply-input", useUnmergedTree = true)
            .performScrollTo()
            .performTextInput(replyBody)
        hideSoftKeyboard()
        composeRule.onNodeWithTag("ustagram-reply-send", useUnmergedTree = true).performClick()
        assertReplyTextDisplayed(publishedPostId, replyBody)

        composeRule.onNodeWithTag("ustagram-generate", useUnmergedTree = true).performClick()
        var generatedBody: String? = null
        composeRule.waitUntil(timeoutMillis = 15_000) {
            val generated = WorldStore(context).use { store ->
                store.postDeletionTargets("moment")
                    .firstOrNull { target ->
                        target.authorKind != "user" &&
                            target.id !in residentIdsBefore &&
                            target.body.isNotBlank() &&
                            target.body != "..."
                    }
            }
            generatedBody = generated?.body
            generated != null || composeRule.onAllNodesWithTag("ustagram-status", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        generatedBody?.let(::assertFeedTextDisplayed)
            ?: composeRule.onNodeWithTag("ustagram-status", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("ustagram-back", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("desktop-hub-sheet", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
        assertTrue(
            composeRule.onAllNodesWithTag("ustagram-screen", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    private fun assertReplyTextDisplayed(
        postId: Long,
        text: String,
        timeoutMillis: Long = 5_000,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.waitUntil(timeoutMillis) {
            WorldStore(context).use { store ->
                store.comments(postId).any { comment -> comment.body == text }
            }
        }
        composeRule.waitForIdle()
        hideSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText(text, substring = true, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun assertFeedTextDisplayed(
        text: String,
        authorKind: String? = null,
        timeoutMillis: Long = 5_000,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.waitUntil(timeoutMillis) {
            WorldStore(context).use { store ->
                val persisted = store.postDeletionTargets("moment").any { target ->
                    target.body == text && (authorKind == null || target.authorKind == authorKind)
                }
                val visible = store.posts("moment").any { post ->
                    post.body == text && (authorKind == null || post.authorKind == authorKind)
                }
                persisted && visible
            }
        }
        composeRule.waitForIdle()
        hideSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun hideSoftKeyboard() {
        composeRule.activityRule.scenario.onActivity { activity ->
            val inputMethodManager = activity.getSystemService(
                android.content.Context.INPUT_METHOD_SERVICE,
            ) as android.view.inputmethod.InputMethodManager
            activity.currentFocus?.let { focused ->
                inputMethodManager.hideSoftInputFromWindow(focused.windowToken, 0)
            }
        }
    }
}
