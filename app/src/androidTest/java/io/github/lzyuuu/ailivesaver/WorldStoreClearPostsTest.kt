package io.github.lzyuuu.ailivesaver

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorldStoreClearPostsTest {
    @After
    fun cleanupMoments() {
        clearMomentPostsForSmoke(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun clearPostsUsesPerPostCleanupAndPreservesOtherKinds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WorldStore(context).use { store ->
            DesktopSeed.ensureDesktopWorld(
                store = store,
                userName = "Tester",
                context = context,
            )
            store.clearPosts("moment")
            val momentBaseline = store.postDeletionTargets("moment").map { it.id }.toSet()

            val root = store.characters(includeDeparted = false)
                .first { it.name == DesktopSeed.ROOT_NAME }
            val visibleId = store.createPost(
                kind = "moment",
                authorName = store.userName(),
                title = "",
                body = "moment post",
                authorKind = "user",
                worldEventKind = "moment",
                eventNeedsResponse = false,
            )
            val hiddenId = store.createPost(
                kind = "moment",
                authorName = root.name,
                title = "",
                body = "hidden moment",
                authorKind = "resident",
                authorCharacterId = root.id,
                eventNeedsResponse = false,
            )
            store.hideAiPost(hiddenId)
            val pendingId = store.createMediaPost(
                body = "pending caption",
                prompt = "sunset over water",
            )
            val forumId = store.createPost(
                kind = "forum",
                authorName = store.userName(),
                title = "topic",
                body = "forum post",
                authorKind = "user",
            )
            val createdMomentIds = setOf(visibleId, hiddenId, pendingId)
            assertTrue(store.worldEvents(limit = null).any { it.sourcePostId == visibleId })
            assertEquals(1, store.posts("moment").count { it.id == visibleId })
            assertFalse(store.posts("moment").any { it.id == hiddenId })
            assertFalse(store.posts("moment").any { it.id == pendingId })

            val cleared = store.clearPosts("moment")

            assertEquals(createdMomentIds.size, cleared)
            assertEquals(
                momentBaseline,
                store.postDeletionTargets("moment").map { it.id }.toSet(),
            )
            assertTrue(store.posts("forum").any { it.id == forumId })
            assertTrue(store.worldEvents(limit = null).none { it.sourcePostId == visibleId })
            assertTrue(store.worldEvents(limit = null).none { it.sourcePostId == hiddenId })
        }
    }
}
