package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RebbitSubredditTest {
    @Test
    fun normalizesSubredditNames() {
        assertEquals("general", normalizeSubreddit("r/general"))
        assertEquals("MidnightOil", normalizeSubreddit(" MidnightOil "))
        assertEquals("Lai", normalizeSubreddit("r/Lai!!!"))
        assertEquals("", normalizeSubreddit("!!!"))
    }

    @Test
    fun forumSortOrdersDifferBetweenBestHotTopAndLatest() {
        val score = "COALESCE((SELECT SUM(value) FROM social_post_votes WHERE post_id = social_posts.id), 0)"
        val best = forumPostsOrderClause("best", score)
        val hot = forumPostsOrderClause("hot", score)
        val top = forumPostsOrderClause("top", score)
        val latest = forumPostsOrderClause("latest", score)
        assertNotEquals(best, hot)
        assertNotEquals(hot, top)
        assertNotEquals(top, latest)
        assertTrue(best.contains("social_comments"))
        assertTrue(hot.contains("3600000.0"))
        assertTrue(top.startsWith(score))
    }

    @Test
    fun formatsRelativeTimeBuckets() {
        val now = 1_700_000_000_000L
        assertEquals("0 minutes ago", rebbitRelativeTime(now, now))
        assertEquals("3 minutes ago", rebbitRelativeTime(now - 3 * 60_000L, now))
        assertEquals("2 hours ago", rebbitRelativeTime(now - 2 * 60 * 60_000L, now))
        assertEquals("1 days ago", rebbitRelativeTime(now - 26 * 60 * 60_000L, now))
    }

    @Test
    fun buildsRebbitHandles() {
        assertEquals("root", rebbitHandle("Root"))
        assertEquals("lai", rebbitHandle("Lai · NPC"))
        assertEquals("midnight", rebbitHandle("Midnight!!"))
    }

    @Test
    fun simulatedGenerateBodyIsNonEmptyAndDeterministic() {
        val first = simulatedRebbitPostBody("Root", "general", seed = 42L)
        val second = simulatedRebbitPostBody("Root", "general", seed = 42L)
        assertTrue(first.isNotBlank())
        assertTrue(first != "...")
        assertEquals(first, second)
        assertTrue(first.contains("general"))
    }

    @Test
    fun rebbitPaletteMatchesLiveReference() {
        // 参考 V4.51（ref-72）：深色 chrome + 金 accent，批次 D 迁移。
        assertEquals(ReferencePalette.PageBg.toArgb(), RebbitStatusBar.toArgb())
        assertEquals(ReferencePalette.PageBg.toArgb(), RebbitChrome.toArgb())
        assertEquals(ReferencePalette.Card.toArgb(), RebbitCard.toArgb())
        assertEquals(0xFF000000.toInt(), RebbitNavBar.toArgb())
    }

    @Test
    fun rebbitSystemBarConfigUsesChromeBarsOnSdk35AndNewer() {
        val config = rebbitSystemBarConfig(35)
        assertEquals(RebbitChrome.toArgb(), config.statusBarColor.toArgb())
        assertEquals(RebbitChrome.toArgb(), config.navigationBarColor.toArgb())
        assertTrue(config.statusBarUsesDarkIcons)
        assertTrue(config.navigationBarUsesDarkIcons)
    }

    @Test
    fun rebbitSystemBarConfigUsesLegacyBarsBelowSdk35() {
        val config = rebbitSystemBarConfig(34)
        assertEquals(RebbitStatusBar.toArgb(), config.statusBarColor.toArgb())
        assertEquals(RebbitNavBar.toArgb(), config.navigationBarColor.toArgb())
        assertFalse(config.statusBarUsesDarkIcons)
        assertFalse(config.navigationBarUsesDarkIcons)
    }

    @Test
    fun rejectsBlankAndEllipsisGeneratedBodies() {
        assertFalse(isValidRebbitGeneratedBody(""))
        assertFalse(isValidRebbitGeneratedBody("   "))
        assertFalse(isValidRebbitGeneratedBody("..."))
        assertFalse(isValidRebbitGeneratedBody(" ... "))
        assertTrue(isValidRebbitGeneratedBody("Real forum opener"))
        assertEquals(
            simulatedRebbitPostBody("Root", "general"),
            sanitizeRebbitGeneratedBody("...", "Root", "general"),
        )
    }
}
