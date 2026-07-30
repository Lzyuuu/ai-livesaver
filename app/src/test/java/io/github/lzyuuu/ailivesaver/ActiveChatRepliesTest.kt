package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveChatRepliesTest {
    @Test
    fun cancelStreamSurvivesSimulatedReentry() {
        val characterId = 42L
        val handle = ProviderStreamHandle()
        ActiveChatReplies.add(characterId)
        ActiveChatReplies.attachStream(characterId, handle)

        ActiveChatReplies.cancelStream(characterId)
        assertTrue(handle.isCancelled())

        ActiveChatReplies.remove(characterId)
        assertFalse(ActiveChatReplies.contains(characterId))
    }
}
