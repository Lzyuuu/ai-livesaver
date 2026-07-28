package io.github.lzyuuu.ailivesaver

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SocialResponsePersistenceSmokeTest {
    @Test
    fun rejectsLateResidentSocialActivityAfterDeparture() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = WorldStore(context)
        val characterName = "Social departure ${System.nanoTime()}"
        val characterId = store.addCharacter(
            name = characterName,
            persona = "Test resident",
            attentionTier = "resident",
            appearance = "",
            clothing = "",
            negativePrompt = "",
        )
        val postId = store.createPost("moment", "", "departure response target")
        val initialEventCount = store.worldEvents(limit = null)
            .count { it.sourcePostId == postId }

        try {
            store.setCharacterActive(characterId, false)
            assertFalse(
                store.addGeneratedSocialResponse(
                    postId,
                    "moment",
                    "Late social response",
                    "Departed resident",
                    characterId,
                    "DeepSeek",
                    "test-model",
                ),
            )
            assertTrue(store.comments(postId).isEmpty())
            assertEquals(
                initialEventCount,
                store.worldEvents(limit = null).count { it.sourcePostId == postId },
            )
            assertTrue(
                runCatching {
                    store.createPost(
                        kind = "moment",
                        authorName = "Departed resident",
                        title = "",
                        body = "Late generated post",
                        authorKind = "resident",
                        authorCharacterId = characterId,
                    )
                }.isFailure,
            )
            assertTrue(store.posts("moment").none { it.body == "Late generated post" })
        } finally {
            store.deleteUserPost(postId)
            store.deleteCharacter(characterId)
            store.writableDatabase.delete(
                "world_events",
                "actor_name = ?",
                arrayOf(characterName),
            )
            store.close()
        }
    }

    @Test
    fun commitsAResponseAtomicallyAndRejectsLateResults() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = WorldStore(context)
        val postId = store.createPost("moment", "", "response target")
        val initialEventCount = store.worldEvents(limit = 100)
            .count { it.sourcePostId == postId }

        try {
            store.writableDatabase.execSQL(
                """
                CREATE TEMP TRIGGER reject_test_response_event
                BEFORE INSERT ON world_events
                BEGIN
                    SELECT RAISE(ABORT, 'test rejection');
                END
                """.trimIndent(),
            )
            assertTrue(
                runCatching {
                    store.addGeneratedSocialResponse(
                        postId,
                        "moment",
                        "first response",
                        "Mira",
                        null,
                        "DeepSeek",
                        "test-model",
                    )
                }.isFailure,
            )
            assertTrue(store.comments(postId).isEmpty())
            assertEquals(
                initialEventCount,
                store.worldEvents(limit = 100).count { it.sourcePostId == postId },
            )

            store.writableDatabase.execSQL("DROP TRIGGER reject_test_response_event")
            assertTrue(
                store.addGeneratedSocialResponse(
                    postId,
                    "moment",
                    "saved response",
                    "Mira",
                    null,
                    "DeepSeek",
                    "test-model",
                ),
            )
            assertEquals(1, store.comments(postId).size)
            assertEquals(
                initialEventCount + 1,
                store.worldEvents(limit = 100).count { it.sourcePostId == postId },
            )

            store.writableDatabase.execSQL(
                "UPDATE social_posts SET ai_responses_enabled = 0 WHERE id = ?",
                arrayOf(postId),
            )
            assertFalse(
                store.addGeneratedSocialResponse(
                    postId,
                    "moment",
                    "late response",
                    "Mira",
                    null,
                    "DeepSeek",
                    "test-model",
                ),
            )
            assertEquals(1, store.comments(postId).size)
        } finally {
            runCatching {
                store.writableDatabase.execSQL("DROP TRIGGER IF EXISTS reject_test_response_event")
            }
            store.deleteUserPost(postId)
            assertFalse(
                store.addGeneratedSocialResponse(
                    postId,
                    "moment",
                    "after deletion",
                    "Mira",
                    null,
                    "DeepSeek",
                    "test-model",
                ),
            )
            store.close()
        }
    }
}
