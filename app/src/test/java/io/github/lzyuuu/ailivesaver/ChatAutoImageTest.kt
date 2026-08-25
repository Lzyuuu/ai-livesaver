package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAutoImageTest {

    @Test
    fun autoImageToggleGatesIntentCreation() {
        assertTrue(
            shouldQueueReplyImage(
                defaultChatControls().copy(autoImageGeneration = true),
                "今晚路过我们第一次见面的那家书店",
            ),
        )
        assertFalse(shouldQueueReplyImage(defaultChatControls(), "任意回复"))
        assertFalse(shouldQueueReplyImage(defaultChatControls().copy(autoImageGeneration = true), "   "))
    }

    @Test
    fun imageIntentPromptComposesVisualIdentityWithScene() {
        assertEquals(
            "silver hair, black coat, moonlight over Tokyo",
            composeImageIntentPrompt(" silver hair ", "black coat", "", "moonlight over Tokyo"),
        )
        assertEquals("", composeImageIntentPrompt("", "", "", ""))
    }
}
