package io.github.lzyuuu.ailivesaver

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import android.app.Notification

class WorldTurnTest {
    @Test
    fun resumesOnlyForBootCompletedBroadcast() {
        assertTrue(isWorldBootAction(Intent.ACTION_BOOT_COMPLETED))
        assertFalse(isWorldBootAction(Intent.ACTION_TIME_CHANGED))
        assertFalse(isWorldBootAction(null))
        assertTrue(shouldKeepContinuousWorldService(enabled = true, continuous = true))
        assertFalse(shouldKeepContinuousWorldService(enabled = false, continuous = true))
        assertFalse(shouldKeepContinuousWorldService(enabled = true, continuous = false))
    }

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
        assertEquals("Noa", npcProfileForEvent(4).first)
        assertEquals("Lin", npcProfileForEvent(9).first)
        assertEquals("Yuki", npcProfileForEvent(14).first)
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

    @Test
    fun attachesImagesOnlyToVisualResidentPosts() {
        assertTrue(shouldAttachWorldImage(2, "post", hasVisualIdentity = true))
        assertTrue(shouldAttachWorldImage(6, "interaction", hasVisualIdentity = true))
        assertFalse(shouldAttachWorldImage(1, "post", hasVisualIdentity = true))
        assertFalse(shouldAttachWorldImage(2, "message", hasVisualIdentity = true))
        assertFalse(shouldAttachWorldImage(2, "post", hasVisualIdentity = false))
    }

    @Test
    fun routesStandalonePostTurnsAcrossAuthorizedPlatforms() {
        assertEquals("ustagram", choosePostPlatform(0, allowY = true, allowUstagram = true))
        assertEquals("y", choosePostPlatform(1, allowY = true, allowUstagram = true))
        assertEquals("y", choosePostPlatform(0, allowY = true, allowUstagram = false))
        assertEquals("y", choosePostPlatform(3, allowY = true, allowUstagram = false))
        assertEquals("ustagram", choosePostPlatform(1, allowY = false, allowUstagram = true))
        assertEquals("none", choosePostPlatform(4, allowY = false, allowUstagram = false))
    }

    @Test
    fun executionGuardRechecksPlatformAuthorization() {
        val lockedDown = ChatControls()
        assertFalse(postAllowed(lockedDown, Y_POST_KIND))
        assertFalse(postAllowed(lockedDown, "moment"))
        assertFalse(postAllowed(lockedDown, "forum"))
        val permissive = defaultChatControls()
        assertTrue(postAllowed(permissive, Y_POST_KIND))
        assertTrue(postAllowed(permissive, "moment"))
        assertTrue(postAllowed(permissive, "forum"))
    }
}
