package io.github.lzyuuu.ailivesaver

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SocialDomainPersistenceSmokeTest {
    private lateinit var context: Context
    private val db = "social-domain-${System.nanoTime()}.db"
    private val ids = mutableListOf<Long>()

    @Before fun setUp() { context = ApplicationProvider.getApplicationContext() }
    @After fun tearDown() { context.deleteDatabase(db) }

    @Test fun kindsAreIsolatedAndUserCrudSurvivesReopen() {
        val moment: Long
        val forum: Long
        val y: Long
        WorldStore(context, db).use { store ->
            moment = store.createPost("moment", "me", "", "moment body", "user", audience = "selected", audienceCharacterIds = "7")
            forum = store.createPost("forum", "me", "forum title", "forum body", "user", subreddit = "general")
            y = store.createPost(Y_POST_KIND, "me", "", "y body", "user", audience = "world")
            ids += listOf(moment, forum, y)
            assertEquals(listOf(moment), store.posts("moment").filter { it.id == moment }.map { it.id })
            assertTrue(store.posts("forum").none { it.id == moment || it.id == y })
            assertTrue(store.posts(Y_POST_KIND).none { it.id == moment || it.id == forum })
            store.toggleReaction(moment)
            store.voteOnPost(forum, 1)
            store.voteOnPost(forum, -1)
            store.addComment(y, "root")
            val parent = store.comments(y).first().id
            store.addComment(y, "nested", parentId = parent)
            assertEquals(-1, store.posts("forum").first { it.id == forum }.voteScore)
            assertEquals(2, store.comments(y).size)
            assertTrue(store.updateUserPost(moment, "", "edited moment"))
        }
        WorldStore(context, db).use { reopened ->
            assertEquals("edited moment", reopened.posts("moment").first { it.id == moment }.body)
            assertEquals(1, reopened.posts("moment").first { it.id == moment }.reactionCount)
            assertEquals(2, reopened.comments(y).size)
            assertEquals("general", reopened.posts("forum").first { it.id == forum }.subreddit)
            reopened.deleteUserPost(moment)
            assertFalse(reopened.posts("moment").any { it.id == moment })
            assertTrue(reopened.posts("forum").any { it.id == forum })
            assertTrue(reopened.posts(Y_POST_KIND).any { it.id == y })
        }
    }
}
