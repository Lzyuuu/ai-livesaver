package io.github.lzyuuu.ailivesaver

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessengerDrawerTest {
    @Test fun controlsPersistAndToggleIndependently() {
        val initial = ChatControls()
        val changed = initial.withToggle(ChatControl.AutoImage, true)
            .withToggle(ChatControl.AllowY, true)
        assertTrue(changed.autoImageGeneration)
        assertTrue(changed.allowPostY)
        assertEquals(false, changed.allowPostUstagram)
    }

    @Test fun leavingWhileStreamingRequiresConfirmation() {
        assertTrue(shouldConfirmChatExit(true, false))
        assertTrue(shouldConfirmChatExit(false, true))
        assertEquals(false, shouldConfirmChatExit(false, false))
    }

    @Test fun chatControlsSaverRoundTripsEveryFlag() {
        val original = ChatControls(
            autoImageGeneration = true,
            allowPostY = false,
            allowPostUstagram = true,
            allowPostRebbit = false,
            webSearchEnabled = true,
        )
        val saved = with(ChatControlsSaver) { SaverScope { true }.save(original) }
        assertNotNull(saved)
        assertEquals(original, with(ChatControlsSaver) { restore(saved!!) })
    }

    @Test fun authorizationDefaultsOnWhileAutomationDefaultsOff() {
        val defaults = defaultChatControls()
        assertTrue(defaults.allowPostY)
        assertTrue(defaults.allowPostUstagram)
        assertTrue(defaults.allowPostRebbit)
        assertEquals(false, defaults.autoImageGeneration)
        assertEquals(false, defaults.webSearchEnabled)
    }

    @Test fun chatControlsKvCodecRoundTripsEveryFlag() {
        val original = ChatControls(
            autoImageGeneration = true,
            allowPostY = false,
            allowPostUstagram = true,
            allowPostRebbit = false,
            webSearchEnabled = true,
        )
        assertEquals(original, decodeChatControls(encodeChatControls(original)))
        assertEquals(false, decodeChatControls(encodeChatControls(ChatControls())).autoImageGeneration)
    }

    @Test fun chatControlsKvMissingRecordFallsBackToDefaults() {
        assertEquals(defaultChatControls(), decodeChatControls(""))
        val partial = decodeChatControls("auto_image=1")
        assertTrue(partial.autoImageGeneration)
        assertTrue(partial.allowPostY)
        assertTrue(partial.allowPostRebbit)
        assertEquals(false, partial.webSearchEnabled)
    }

    @Test fun manualMemoryAttachesToLatestActiveUserMessage() {
        val retired = ChatMessage(3L, 7L, "user", "old", 3L, active = false)
        val current = ChatMessage(1L, 7L, "user", "hi", 1L)
        val assistant = ChatMessage(2L, 7L, "character:7", "hello", 2L)
        assertEquals(
            1L,
            latestUserMessageForMemory(listOf(retired, current, assistant))?.id,
        )
        assertEquals(null, latestUserMessageForMemory(listOf(assistant)))
        assertEquals(null, latestUserMessageForMemory(emptyList()))
    }
}
