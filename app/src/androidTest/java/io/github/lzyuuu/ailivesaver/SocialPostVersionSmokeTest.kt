package io.github.lzyuuu.ailivesaver

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SocialPostVersionSmokeTest {
    @Test
    fun keepsAUserPostAndItsWorldExcerptInSync() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = WorldStore(context)
        val postId = store.createPost("forum", "Old title", "Old body")

        try {
            assertTrue(store.updateUserPost(postId, "New title", "New body"))
            val post = store.posts("forum").first { it.id == postId }
            val event = store.worldEvents(limit = 100).first { it.sourcePostId == postId }
            assertEquals("New title", post.title)
            assertEquals("New body", post.body)
            assertEquals("New title", event.summary)
        } finally {
            store.deleteUserPost(postId)
            store.close()
        }
    }

    @Test
    fun rejectsARewriteWhenTheSourcePostHasChangedOrDisappeared() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = WorldStore(context)
        val postId = store.createPost(
            kind = "moment",
            authorName = "Mira",
            title = "",
            body = "Original post",
            authorKind = "resident",
        )

        try {
            assertTrue(
                store.rewriteAiPost(
                    postId,
                    "Original post",
                    "Current post",
                    "DeepSeek",
                    "test-model",
                ),
            )
            assertEquals(1, store.socialPostVersions(postId).size)
            val rewrittenEvent = store.worldEvents(limit = 100)
                .first { it.sourcePostId == postId }
            assertEquals("Current post", rewrittenEvent.summary)
            assertEquals("DeepSeek", rewrittenEvent.providerName)
            assertEquals("test-model", rewrittenEvent.modelName)
            assertFalse(
                store.rewriteAiPost(
                    postId,
                    "Original post",
                    "Late stale post",
                    "DeepSeek",
                    "test-model",
                ),
            )
            assertEquals(1, store.socialPostVersions(postId).size)
            assertEquals("Current post", store.posts("moment").first { it.id == postId }.body)

            val originalVersion = store.socialPostVersions(postId).single()
            assertTrue(store.restoreAiPostVersion(postId, originalVersion.id))
            assertEquals("Original post", store.posts("moment").first { it.id == postId }.body)
            assertEquals(2, store.socialPostVersions(postId).size)
            val restoredEvent = store.worldEvents(limit = 100)
                .first { it.sourcePostId == postId }
            assertEquals("Original post", restoredEvent.summary)
            assertEquals("", restoredEvent.providerName)
            assertEquals("", restoredEvent.modelName)
            assertFalse(
                store.rewriteAiPost(
                    postId,
                    "Current post",
                    "Late after restore",
                    "DeepSeek",
                    "test-model",
                ),
            )

            store.hideAiPost(postId)
            assertFalse(
                store.rewriteAiPost(
                    postId,
                    "Original post",
                    "After hiding",
                    "DeepSeek",
                    "test-model",
                ),
            )
            assertEquals(2, store.socialPostVersions(postId).size)

            store.deleteAiPost(postId)
            assertFalse(
                store.rewriteAiPost(
                    postId,
                    "Original post",
                    "After deletion",
                    "DeepSeek",
                    "test-model",
                ),
            )
        } finally {
            store.deleteAiPost(postId)
            store.close()
        }
    }
}
