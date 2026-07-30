package io.github.lzyuuu.ailivesaver

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatPersistenceSmokeTest {
    @Test
    fun rejectsLateRepliesAndRollsBackProactiveMessagesAfterDeparture() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = WorldStore(context)
        val characterName = "Departure test ${System.nanoTime()}"
        val characterId = store.addCharacter(
            name = characterName,
            persona = "Test resident",
            attentionTier = "resident",
            appearance = "",
            clothing = "",
            negativePrompt = "",
        )
        val replyId = store.beginAssistantReply(characterId, "Test Provider", "test-model").id

        try {
            store.writableDatabase.execSQL(
                """
                CREATE TEMP TRIGGER reject_test_proactive_event
                BEFORE INSERT ON world_events
                BEGIN
                    SELECT RAISE(ABORT, 'test rejection');
                END
                """.trimIndent(),
            )
            assertTrue(
                runCatching {
                    store.addProactiveMessage(
                        characterId,
                        "Atomic proactive message",
                        "message",
                        "Test resident",
                        "Test Provider",
                        "test-model",
                    )
                }.isFailure,
            )
            assertTrue(store.messages(characterId).none { it.body == "Atomic proactive message" })
            store.writableDatabase.execSQL("DROP TRIGGER reject_test_proactive_event")

            store.setCharacterActive(characterId, false)
            assertFalse(
                store.addProactiveMessage(
                    characterId,
                    "Late proactive message",
                    "message",
                    "Test resident",
                    "Test Provider",
                    "test-model",
                ),
            )
            assertTrue(
                runCatching {
                    store.completeAssistantReply(
                        replyId,
                        "Late private reply",
                        "Test Provider",
                        "test-model",
                    )
                }.isFailure,
            )
            assertTrue(store.messageVersions(replyId).isEmpty())
            assertEquals("", store.messages(characterId).first { it.id == replyId }.body)
            assertTrue(
                runCatching {
                    store.beginAssistantReply(characterId, "Test Provider", "test-model")
                }.isFailure,
            )
        } finally {
            runCatching {
                store.writableDatabase.execSQL("DROP TRIGGER IF EXISTS reject_test_proactive_event")
            }
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
    fun completesFromAReopenedStoreAndRejectsAStaleRecap() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val characterId = WorldStore(context).use {
            it.addCharacter(
                name = "Callback test ${System.nanoTime()}",
                persona = "Test resident",
                attentionTier = "resident",
                appearance = "",
                clothing = "",
                negativePrompt = "",
            )
        }

        try {
            val userMessage = WorldStore(context).use {
                it.addMessage(characterId, "user", "Original timeline")
            }
            val replyId = WorldStore(context).use {
                it.beginAssistantReply(characterId, "Test Provider", "test-model").id
            }
            WorldStore(context).use {
                it.completeAssistantReply(
                    replyId,
                    "Completed after the originating store closed",
                    "Test Provider",
                    "test-model",
                )
            }
            WorldStore(context).use {
                assertEquals("complete", it.messages(characterId).last().status)
                assertEquals(1, it.messageVersions(replyId).size)
            }

            val rewritten = WorldStore(context).use {
                it.rewriteFromMessage(characterId, userMessage.id, "Current timeline")
            }
            WorldStore(context).use {
                assertFalse(
                    it.saveConversationRecapIfCurrent(
                        characterId,
                        "Stale recap",
                        replyId,
                    ),
                )
                assertTrue(
                    it.saveConversationRecapIfCurrent(
                        characterId,
                        "Current recap",
                        rewritten.id,
                    ),
                )
                assertEquals(rewritten.id, it.conversationRecap(characterId)?.throughMessageId)
            }

            WorldStore(context).use {
                it.writableDatabase.execSQL(
                    "UPDATE characters SET active = 0 WHERE id = ?",
                    arrayOf(characterId),
                )
                it.deleteCharacter(characterId)
            }
            assertFalse(
                WorldStore(context).use {
                    it.saveConversationRecapIfCurrent(
                        characterId,
                        "After deletion",
                        rewritten.id,
                    )
                },
            )
        } finally {
            WorldStore(context).use {
                it.writableDatabase.execSQL(
                    "UPDATE characters SET active = 0 WHERE id = ?",
                    arrayOf(characterId),
                )
                it.deleteCharacter(characterId)
            }
        }
    }

    @Test
    fun keepsOfflineMessageAndInterruptsStreamingDraftAfterReopen() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val characterId = WorldStore(context).use {
            it.addCharacter(
                name = "Restart test ${System.nanoTime()}",
                persona = "Test resident",
                attentionTier = "resident",
                appearance = "",
                clothing = "",
                negativePrompt = "",
            )
        }

        try {
            WorldStore(context).use { it.addMessage(characterId, "user", "Still there?") }
            WorldStore(context).use { store ->
                val messages = store.messages(characterId)
                assertEquals(1, messages.size)
                assertEquals("user", messages.single().sender)
                assertEquals("Still there?", messages.single().body)
            }

            val replyId = WorldStore(context).use { store ->
                store.beginAssistantReply(characterId, "Test Provider", "test-model").id.also {
                    store.updateAssistantDraft(it, "I was saying")
                }
            }
            WorldStore(context).use { store ->
                assertEquals(1, store.recoverInterruptedReplies(characterId))
                assertEquals(0, store.recoverInterruptedReplies(characterId))
                val reply = store.messages(characterId).first { it.id == replyId }
                assertEquals("interrupted", reply.status)
                assertEquals("", reply.body)
                assertEquals("I was saying", reply.draftBody)
                assertTrue(store.messageVersions(replyId).isEmpty())
            }
            WorldStore(context).use { store ->
                assertEquals("interrupted", store.messages(characterId).last().status)
            }
        } finally {
            WorldStore(context).use {
                it.writableDatabase.execSQL(
                    "UPDATE characters SET active = 0 WHERE id = ?",
                    arrayOf(characterId),
                )
                it.deleteCharacter(characterId)
            }
        }
    }

    @Test
    fun clearsActiveConversationWhileKeepingRetiredHistory() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val characterId = WorldStore(context).use {
            it.addCharacter(
                name = "Clear test ${System.nanoTime()}",
                persona = "Test resident",
                attentionTier = "resident",
                appearance = "",
                clothing = "",
                negativePrompt = "",
            )
        }
        try {
            WorldStore(context).use { store ->
                val user = store.addMessage(characterId, "user", "hello clear")
                store.rememberIfCurrent(characterId, user.id, "user said hello clear")
                store.clearConversation(characterId)
                assertTrue(store.messages(characterId).isEmpty())
                assertTrue(store.retiredMessages(characterId).any { it.body == "hello clear" })
                assertTrue(store.memories(characterId).isEmpty())
            }
        } finally {
            WorldStore(context).use {
                it.writableDatabase.execSQL(
                    "UPDATE characters SET active = 0 WHERE id = ?",
                    arrayOf(characterId),
                )
                it.deleteCharacter(characterId)
            }
        }
    }

    @Test
    fun interruptAssistantReplyMarksStreamingAsInterrupted() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val characterId = WorldStore(context).use {
            it.addCharacter(
                name = "Stop test ${System.nanoTime()}",
                persona = "Test resident",
                attentionTier = "resident",
                appearance = "",
                clothing = "",
                negativePrompt = "",
            )
        }
        try {
            WorldStore(context).use { store ->
                val reply = store.beginAssistantReply(characterId, "Test Provider", "test-model")
                store.updateAssistantDraft(reply.id, "partial")
                store.interruptAssistantReply(reply.id)
                val saved = store.messages(characterId).first { it.id == reply.id }
                assertEquals("interrupted", saved.status)
                assertEquals("partial", saved.draftBody)
            }
        } finally {
            WorldStore(context).use {
                it.writableDatabase.execSQL(
                    "UPDATE characters SET active = 0 WHERE id = ?",
                    arrayOf(characterId),
                )
                it.deleteCharacter(characterId)
            }
        }
    }
}
