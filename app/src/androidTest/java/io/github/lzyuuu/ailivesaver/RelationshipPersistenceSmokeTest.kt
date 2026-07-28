package io.github.lzyuuu.ailivesaver

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelationshipPersistenceSmokeTest {
    @Test
    fun migratesAndPersistsHiddenRelationshipDimensions() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val characterId = WorldStore(context).use { store ->
            assertEquals(
                WORLD_DATABASE_VERSION,
                store.readableDatabase.version,
            )
            store.addCharacter(
                name = "Relationship dimensions ${System.nanoTime()}",
                persona = "Test resident",
                attentionTier = "resident",
                appearance = "",
                clothing = "",
                negativePrompt = "",
            ).also { id ->
                val message = store.addMessage(id, "user", "我喜欢在周末散步")
                store.recordConversationRelationship(
                    id,
                    message.id,
                    sharedPersonalFact = true,
                )
            }
        }

        try {
            WorldStore(context).use { store ->
                val relationship = store.relationship(characterId)
                assertEquals("开始交谈", relationship.label)
                assertEquals(1, relationship.closeness)
                assertEquals(1, relationship.trust)
                assertEquals(0, relationship.tension)
                val event = store.relationshipEvents(characterId).single()
                assertEquals(relationship.closeness, event.closeness)
                assertEquals(relationship.trust, event.trust)
                assertEquals(relationship.tension, event.tension)
                assertTrue(relationshipBehaviorGuidance(relationship).isNotBlank())
            }
        } finally {
            WorldStore(context).use { store ->
                store.writableDatabase.execSQL(
                    "UPDATE characters SET active = 0 WHERE id = ?",
                    arrayOf(characterId),
                )
                store.deleteCharacter(characterId)
            }
        }
    }
}
