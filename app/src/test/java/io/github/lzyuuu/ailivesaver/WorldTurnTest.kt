package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldTurnTest {
    @Test
    fun keepsNpcActivityBoundedAndHonorsCharacterControls() {
        val turns = (0 until 10).map { chooseWorldTurn(it, true, true) }
        assertEquals(2, turns.count { it == "npc" })
        assertEquals("none", chooseWorldTurn(0, false, false))
        assertEquals("message", chooseWorldTurn(0, true, false))
        assertEquals("post", chooseWorldTurn(0, false, true))
        assertEquals(0, budgetUsedForDay("2026-07-26", "2026-07-27", 20))
        assertEquals(20, budgetUsedForDay("2026-07-27", "2026-07-27", 20))
        assertEquals(false, allowsAutomaticInference(20, 20))
        assertEquals(true, allowsAutomaticInference(0, 999))
        assertEquals(true, isDefaultQuietHour(23))
        assertEquals(true, isDefaultQuietHour(7))
        assertEquals(false, isDefaultQuietHour(12))
    }
}
