package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YFeedTest {
    @Test
    fun buildsTwitterStyleHandles() {
        assertEquals("@root", yHandle("Root"))
        assertEquals("@lai", yHandle("Lai"))
        assertEquals("@jett", yHandle("Jett · NPC"))
    }

    @Test
    fun fallsBackWhenHandleNormalizesEmpty() {
        assertEquals("@user", yHandle(""))
        assertEquals("@user", yHandle("   "))
        assertEquals("@user", yHandle("!!!"))
        assertEquals("@user", yHandle("·"))
    }

    @Test
    fun routesYWorldEventsToYApp() {
        assertEquals(DesktopApp.Y.route, routeWorldEvent("y", "Chats"))
        assertEquals(DesktopApp.Y.route, routeWorldEvent("y_response", "Moments"))
        assertEquals("Moments", routeWorldEvent("moment", "Chats"))
        assertEquals("Commons", routeWorldEvent("forum_reply", "Chats"))
    }

    @Test
    fun keepsNestedReplyOrderForYThreads() {
        val root = SocialComment(1, 9, "Root", "late nights", 1)
        val child = SocialComment(2, 9, "J", "is that so?", 2, parentId = 1)
        val nested = SocialComment(3, 9, "Root", "especially then", 3, parentId = 2)

        assertEquals(
            listOf(1L to 0, 2L to 1, 3L to 2),
            threadedComments(listOf(nested, child, root)).map { it.first.id to it.second },
        )
    }

    @Test
    fun formatsYRelativeTimeInEnglishMinutes() {
        val now = 1_700_000_000_000L
        assertEquals("0 minutes ago", yRelativeTimeLabel(now, now))
        assertEquals("1 minute ago", yRelativeTimeLabel(now - 60_000L, now))
        assertEquals("3 minutes ago", yRelativeTimeLabel(now - 180_000L, now))
    }

    @Test
    fun resolvesGenerateBodyWithoutEllipsisFallback() {
        assertEquals("Hello world", yResolvedGenerateBody("Hello world", "Root"))
        assertEquals(ySimulatedGeneratedPost("Root"), yResolvedGenerateBody("", "Root"))
        assertEquals(ySimulatedGeneratedPost("Root"), yResolvedGenerateBody("...", "Root"))
        assertEquals(ySimulatedGeneratedPost("Root"), yResolvedGenerateBody(null, "Root"))
    }

    @Test
    fun feedItemKeyIncludesRevisionForLazyInvalidation() {
        assertEquals(2 to 9L, yFeedItemKey(2, 9))
        assertNotEquals(yFeedItemKey(1, 9), yFeedItemKey(2, 9))
        assertEquals(yFeedItemKey(4, 1), yFeedItemKey(4, 1))
    }

    @Test
    fun usesLightCreamSocialPalette() {
        assertEquals(FancyCream, YFeedPalette.pageBackground)
        assertEquals(FancyCream, YFeedPalette.chromeBackground)
        assertEquals(FancyCream, YFeedPalette.statusBarColor)
        assertEquals(Color.Black, YFeedPalette.title)
        assertEquals(FancyGold, YFeedPalette.generateAccent)
        assertTrue(YFeedPalette.usesLightSystemBarIcons)
        assertNotEquals(Color(0xFF0A0E14), YFeedPalette.pageBackground)
        assertNotEquals(Color(0xFF111621), YFeedPalette.chromeBackground)
        assertNotEquals(Color(0xFF1A1C1E), YFeedPalette.cardBackground)
    }

    @Test
    fun simulatedGeneratePostIsDeterministicAndNonEmpty() {
        val first = ySimulatedGeneratedPost("Root")
        val second = ySimulatedGeneratedPost("Root")
        assertEquals(first, second)
        assertTrue(first.isNotBlank())
        assertNotEquals("...", first)
        assertTrue(first.contains("Root"))
    }
}
