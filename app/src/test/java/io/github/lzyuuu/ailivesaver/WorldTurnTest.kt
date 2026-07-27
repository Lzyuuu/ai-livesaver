package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import android.app.Notification

class WorldTurnTest {
    @Test
    fun keepsNpcActivityBoundedAndHonorsCharacterControls() {
        val turns = (0 until 10).map { chooseWorldTurn(it, true, true) }
        assertEquals(2, turns.count { it == "npc" })
        assertEquals("interaction", chooseWorldTurn(6, true, true, hasBackgroundPair = true))
        assertEquals(
            "post",
            chooseWorldTurn(6, true, true, hasBackgroundPair = true, allowInteraction = false),
        )
        assertEquals("post", chooseWorldTurn(6, true, true, hasBackgroundPair = false))
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
        assertEquals(
            Notification.VISIBILITY_PRIVATE,
            relationshipNotificationVisibility(preview = false),
        )
        assertEquals(
            Notification.VISIBILITY_PUBLIC,
            relationshipNotificationVisibility(preview = true),
        )
        assertEquals(
            true,
            shouldReconstructWorld(
                previousOpen = 1_000,
                now = 10_000,
                lastEvent = 1_000,
                interval = 5_000,
            ),
        )
        assertEquals(
            false,
            shouldReconstructWorld(
                previousOpen = 1_000,
                now = 10_000,
                lastEvent = 6_000,
                interval = 5_000,
            ),
        )
        assertEquals(
            false,
            shouldReconstructWorld(
                previousOpen = 10_000,
                now = 1_000,
                lastEvent = 0,
                interval = 5_000,
            ),
        )
        assertEquals(true, storageAllowsGeneration(256L * 1024 * 1024))
        assertEquals(false, storageAllowsGeneration(256L * 1024 * 1024 - 1))
        assertEquals(true, isLocalDreamUnavailable(ConnectException("refused")))
        assertEquals(false, isLocalDreamUnavailable(IOException("generation failed")))
        assertEquals(true, isBackedUpWorldSetting("daily_budget"))
        assertEquals(true, isBackedUpWorldSetting("character_42_notifications"))
        assertEquals(true, isBackedUpWorldSetting("global_style"))
        assertEquals(false, isBackedUpWorldSetting("budget_used"))
        assertEquals(false, isBackedUpWorldSetting("character_bad_notifications"))
    }
}
