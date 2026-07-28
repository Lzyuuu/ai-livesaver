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

            WorldStore(context).use { it.deleteCharacter(characterId) }
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
            WorldStore(context).use { it.deleteCharacter(characterId) }
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
            WorldStore(context).use { it.deleteCharacter(characterId) }
        }
    }
}
