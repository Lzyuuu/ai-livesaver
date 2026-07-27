package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Test

class VisualPromptTest {
    @Test
    fun composesIdentityBeforeSceneAndOmitsEmptyParts() {
        val character = ResidentCharacter(
            id = 1,
            name = "Mira",
            persona = "观察者",
            appearance = "短银发",
            clothing = "深蓝外套",
        )

        assertEquals("短银发, 深蓝外套, 雨夜车站", composeVisualPrompt(character, "雨夜车站"))
        assertEquals(
            "短银发, 深蓝外套, 柔和胶片感, 雨夜车站",
            composeVisualPrompt(character, "雨夜车站", "柔和胶片感"),
        )
        assertEquals("深蓝外套, 雨夜车站", composeVisualPrompt(character.copy(appearance = ""), "雨夜车站"))
    }

    @Test
    fun editingUsesCurrentPromptBeforeDescriptionFallback() {
        assertEquals("new scene", mediaPromptForEditing("new scene", "old description"))
        assertEquals("saved description", mediaPromptForEditing("  ", "saved description"))
    }
}
