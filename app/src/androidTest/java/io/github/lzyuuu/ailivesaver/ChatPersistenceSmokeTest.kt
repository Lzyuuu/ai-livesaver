package io.github.lzyuuu.ailivesaver

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatPersistenceSmokeTest {
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
