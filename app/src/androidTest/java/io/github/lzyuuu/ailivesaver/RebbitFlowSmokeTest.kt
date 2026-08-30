package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RebbitFlowSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedRebbitShell() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        writeWelcomeGuideCompleted(context, true)
        resetImagingStudioOnDeviceForSmoke(context)
        WorldStore(context).use { store ->
            store.deleteAllForumPosts()
            DesktopSeed.ensureDesktopWorld(
                store = store,
                userName = "RebbitUi",
                about = "smoke",
                context = context,
            )
            store.ensureDefaultRebbitSubreddits()
            // The publish path always resolves to the first enabled subreddit, so make
            // "general" deterministically enabled before composing. This keeps the posts
            // created by this test visible in the feed regardless of subreddit state left
            // by earlier suite runs (the "None" experiment below disables everything).
            store.setRebbitSubredditEnabled("general", true)
        }
        seedInstallOnHomeForSmoke(context, listOf("rebbit"))
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @After
    fun restoreSubredditVisibility() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WorldStore(context).use { store ->
            // The "None" experiment disables every subreddit, and only the publish path
            // re-enables "general" afterwards. posts("forum") hides posts whose subreddit
            // is disabled, so a left-behind disabled user subreddit would make later
            // suite tests unable to see their own forum posts. Restore the pre-test
            // visibility so this shared world.db stays intact for subsequent tests.
            store.setAllRebbitSubredditsEnabled(true)
        }
    }

    @Test
    fun rebbitForumFlowThroughMainActivityComposeUi() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val postBody = "Rebbit compose smoke ${System.nanoTime()}"

        swipeDesktopToAppsPage(composeRule)
        composeRule.onNodeWithTag("desktop-grid-rebbit").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rebbit-screen").assertIsDisplayed()

        composeRule.onNodeWithTag("rebbit-compose").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rebbit-composer-body").performTextInput(postBody)
        composeRule.onNodeWithTag("rebbit-composer-post").performClick()
        composeRule.waitForIdle()
        val postId = WorldStore(context).use { store ->
            val post = store.posts("forum", "latest").first { it.body == postBody }
            assertTrue(post.body == postBody)
            post.id
        }
        composeRule.assertRebbitPostVisible(postId)
        composeRule.onNodeWithTag("rebbit-feed").assertIsDisplayed()

        composeRule.onNodeWithTag("rebbit-more").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Manage Subreddits", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rebbit-subreddits").assertIsDisplayed()
        composeRule.onNodeWithText("None", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("rebbit-subreddits-done").performClick()
        composeRule.waitForIdle()

        val recoveryBody = "After none recovery ${System.nanoTime()}"
        composeRule.onNodeWithTag("rebbit-compose").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rebbit-composer-body").performTextInput(recoveryBody)
        composeRule.onNodeWithTag("rebbit-composer-post").performClick()
        composeRule.waitForIdle()
        val recoveryPostId = WorldStore(context).use { store ->
            assertTrue(store.rebbitSubreddits().first { it.name == "general" }.enabled)
            val post = store.posts("forum", "latest").first { it.body == recoveryBody }
            post.id
        }
        composeRule.assertRebbitPostVisible(recoveryPostId)

        // The earlier post may belong to the subreddit disabled by the intentional
        // "None" experiment. Exercise detail, voting, and replies on the recovery post,
        // which is guaranteed to be visible in the re-enabled general feed.
        composeRule.openRebbitPost(recoveryPostId)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rebbit-detail").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("顶", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        WorldStore(context).use { store ->
            assertEquals(1, store.posts("forum").first { it.id == recoveryPostId }.voteScore)
        }
        composeRule.onNodeWithContentDescription("踩", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        WorldStore(context).use { store ->
            assertEquals(-1, store.posts("forum").first { it.id == recoveryPostId }.voteScore)
        }

        val replyBody = "UI reply ${System.nanoTime()}"
        composeRule.onNodeWithTag("rebbit-reply-$recoveryPostId").performTextInput(replyBody)
        composeRule.onNodeWithTag("rebbit-send-$recoveryPostId").performClick()
        composeRule.waitForIdle()
        WorldStore(context).use { store ->
            assertTrue(store.comments(recoveryPostId).any { it.body == replyBody })
        }

        composeRule.onNodeWithTag("rebbit-back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rebbit-feed").assertIsDisplayed()

        val existingIds = WorldStore(context).use { store ->
            store.posts("forum").map { it.id }.toSet()
        }
        composeRule.onNodeWithTag("rebbit-generate").performClick()
        composeRule.waitForIdle()
        val generated = pollNewResidentForumPost(context, existingIds)
        assertTrue(isValidRebbitGeneratedBody(generated.body))
        assertFalse(generated.body == "...")
        composeRule.assertRebbitPostVisible(generated.id)

        composeRule.openRebbitPost(generated.id)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rebbit-detail").assertIsDisplayed()
        composeRule.onNodeWithTag("rebbit-back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rebbit-feed").assertIsDisplayed()

        composeRule.onNodeWithTag("rebbit-back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system-desktop").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("desktop-hub-sheet", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    /**
     * The feed is a LazyColumn sorted newest-first, so a post published earlier sits below
     * newer posts (and below the queued-response banner). Whether its card is composed at
     * any instant depends on viewport height and async inference progress, which made
     * direct lookups flaky. The DB already proved the post exists, so scroll the feed to
     * the card before asserting/clicking — still opening the real detail screen through
     * the user-visible card, never bypassing the UI.
     */
    private fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>
        .scrollRebbitFeedTo(postId: Long) {
        onNodeWithTag("rebbit-feed").performScrollToNode(hasTestTag("rebbit-post-$postId"))
    }

    private fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.assertRebbitPostVisible(
        postId: Long,
    ) {
        scrollRebbitFeedTo(postId)
        onNodeWithTag("rebbit-post-$postId", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.openRebbitPost(
        postId: Long,
    ) {
        scrollRebbitFeedTo(postId)
        onNodeWithTag("rebbit-post-$postId", useUnmergedTree = true).performClick()
    }

    private fun pollNewResidentForumPost(
        context: android.content.Context,
        existingIds: Set<Long>,
        timeoutMs: Long = 8_000L,
    ): SocialPost {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            WorldStore(context).use { store ->
                val post = store.posts("forum", "latest").firstOrNull { candidate ->
                    candidate.id !in existingIds &&
                        candidate.authorKind == "resident" &&
                        isValidRebbitGeneratedBody(candidate.body)
                }
                if (post != null) return post
            }
            Thread.sleep(100)
        }
        error("Timed out waiting for generated Rebbit forum post")
    }
}
