package io.github.lzyuuu.ailivesaver

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NpcPublicationSmokeTest {
    @Test
    fun commitsNpcLifecycleAndContentAsOneWorldChange() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = WorldStore(context)
        val suffix = System.nanoTime()
        val oldName = "Old NPC $suffix"
        val forumName = "Forum NPC $suffix"
        val momentName = "Moment NPC $suffix"
        val missingName = "Missing NPC $suffix"
        val oldNpc = store.ensureNpc(oldName, "Existing background member")
        val forumPostId = store.createPost("forum", "NPC target", "Discuss here")

        try {
            store.writableDatabase.execSQL(
                """
                CREATE TEMP TRIGGER reject_test_npc_event
                BEFORE INSERT ON world_events
                BEGIN
                    SELECT RAISE(ABORT, 'test rejection');
                END
                """.trimIndent(),
            )
            assertTrue(
                runCatching {
                    store.publishNpcTurn(
                        forumName,
                        "Temporary forum member",
                        "Failed forum reply",
                        forumPostId,
                        "npc_forum_reply",
                        "DeepSeek",
                        "test-model",
                    )
                }.isFailure,
            )
            assertTrue(store.comments(forumPostId).isEmpty())
            assertTrue(store.npcByName(oldName)?.active == true)
            assertTrue(store.npcByName(forumName) == null)

            assertTrue(
                runCatching {
                    store.publishNpcTurn(
                        momentName,
                        "Temporary social member",
                        "Failed NPC moment",
                        null,
                        "moment",
                        "DeepSeek",
                        "test-model",
                    )
                }.isFailure,
            )
            assertTrue(store.posts("moment").none { it.body == "Failed NPC moment" })
            assertTrue(store.npcByName(momentName) == null)

            store.writableDatabase.execSQL("DROP TRIGGER reject_test_npc_event")
            assertTrue(
                store.publishNpcTurn(
                    forumName,
                    "Temporary forum member",
                    "Committed forum reply",
                    forumPostId,
                    "npc_forum_reply",
                    "DeepSeek",
                    "test-model",
                ),
            )
            assertTrue(store.comments(forumPostId).any { it.body == "Committed forum reply" })
            assertTrue(store.npcByName(forumName)?.active == true)
            assertFalse(store.npcByName(oldName)?.active ?: true)

            store.deleteUserPost(forumPostId)
            assertFalse(
                store.publishNpcTurn(
                    missingName,
                    "Never entered the world",
                    "Late forum reply",
                    forumPostId,
                    "npc_forum_reply",
                    "DeepSeek",
                    "test-model",
                ),
            )
            assertTrue(store.npcByName(missingName) == null)
        } finally {
            runCatching {
                store.writableDatabase.execSQL("DROP TRIGGER IF EXISTS reject_test_npc_event")
            }
            store.deleteUserPost(forumPostId)
            store.writableDatabase.delete(
                "npcs",
                "name IN (?, ?, ?, ?)",
                arrayOf(oldName, forumName, momentName, missingName),
            )
            store.close()
        }
    }
}
