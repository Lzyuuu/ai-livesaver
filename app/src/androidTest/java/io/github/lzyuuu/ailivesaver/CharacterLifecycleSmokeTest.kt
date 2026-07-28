package io.github.lzyuuu.ailivesaver

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CharacterLifecycleSmokeTest {
    @Test
    fun recordsDepartureAndRequiresItBeforePermanentDeletion() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = WorldStore(context)
        val name = "Lifecycle test ${System.nanoTime()}"
        val characterId = store.addCharacter(
            name = name,
            persona = "Test resident",
            attentionTier = "resident",
            appearance = "",
            clothing = "",
            negativePrompt = "",
        )
        store.saveMemberWorldContext("character:$characterId", "Shanghai", "Asia/Shanghai")
        store.addMessage(characterId, "user", "Keep this history")

        try {
            assertFalse(store.deleteCharacter(characterId))
            assertTrue(store.characters().any { it.id == characterId })
            assertEquals("Keep this history", store.messages(characterId).single().body)
            assertEquals("Shanghai", store.memberWorldContext("character:$characterId").location)

            store.writableDatabase.execSQL(
                """
                CREATE TEMP TRIGGER reject_test_lifecycle_event
                BEFORE INSERT ON world_events
                BEGIN
                    SELECT RAISE(ABORT, 'test rejection');
                END
                """.trimIndent(),
            )
            assertTrue(runCatching { store.setCharacterActive(characterId, false) }.isFailure)
            assertTrue(store.characters().first { it.id == characterId }.active)
            store.writableDatabase.execSQL("DROP TRIGGER reject_test_lifecycle_event")

            assertTrue(store.setCharacterActive(characterId, false))
            assertTrue(
                store.worldEvents(limit = null).any {
                    it.kind == "character_departure" && it.actorName == name
                },
            )
            assertTrue(store.setCharacterActive(characterId, true))
            assertTrue(
                store.worldEvents(limit = null).any {
                    it.kind == "character_return" && it.actorName == name
                },
            )
            assertEquals("Keep this history", store.messages(characterId).single().body)

            assertTrue(store.setCharacterActive(characterId, false))
            assertTrue(store.deleteCharacter(characterId))
            assertTrue(store.characters().none { it.id == characterId })
            assertTrue(store.messages(characterId, includeRetired = true).isEmpty())
            assertEquals("", store.memberWorldContext("character:$characterId").location)
            assertFalse(store.deleteCharacter(characterId))
        } finally {
            runCatching {
                store.writableDatabase.execSQL("DROP TRIGGER IF EXISTS reject_test_lifecycle_event")
            }
            store.writableDatabase.delete(
                "world_events",
                "actor_name = ?",
                arrayOf(name),
            )
            store.writableDatabase.delete(
                "member_world_context",
                "member_key = ?",
                arrayOf("character:$characterId"),
            )
            store.writableDatabase.delete(
                "characters",
                "id = ?",
                arrayOf(characterId.toString()),
            )
            store.close()
        }
    }
}
