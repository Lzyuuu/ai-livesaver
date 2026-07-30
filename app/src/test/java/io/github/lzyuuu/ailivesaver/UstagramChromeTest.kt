package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UstagramChromeTest {
    @Test
    fun socialHandleNormalizesAuthorName() {
        assertEquals("@sandra", socialHandle("Sandra"))
        assertEquals("@root", socialHandle("Root"))
        assertEquals("@lai", socialHandle("Lai"))
        assertEquals("@mira", socialHandle("Mira · NPC"))
    }

    @Test
    fun chromeColorsMatchLiveLightReference() {
        assertEquals(0xFFF5F3EE.toInt(), UstagramChrome.toArgb())
        assertEquals(0xFFFFFCF7.toInt(), UstagramCard.toArgb())
        assertEquals(0xFFF5F3EE.toInt(), UstagramFeed.toArgb())
        assertEquals(0xFFE0DDD6.toInt(), UstagramCardBorder.toArgb())
    }

    @Test
    fun relativeTimeBucketsUseEnglishChromeUnits() {
        val now = 1_700_000_000_000L
        assertEquals(UstagramRelativeTime.ZeroMinutes, ustagramRelativeTime(now, now))
        assertEquals(UstagramRelativeTime.Minutes(3), ustagramRelativeTime(now - 3 * 60_000L, now))
        assertEquals(UstagramRelativeTime.Hours(2), ustagramRelativeTime(now - 2 * 60 * 60_000L, now))
        assertEquals(UstagramRelativeTime.Days(1), ustagramRelativeTime(now - 30 * 60 * 60_000L, now))
    }

    @Test
    fun socialHandleFallsBackForEmptyNames() {
        assertTrue(socialHandle("Valerie").startsWith("@"))
        assertEquals("@user", socialHandle("!!!"))
    }

    @Test
    fun postAuthorAvatarPathUsesUserIdentityForUserPosts() {
        val post = samplePost(authorKind = "user", authorName = "You")
        assertEquals("/user.png", ustagramPostAuthorAvatarPath(post, "/user.png", emptyList()))
        assertEquals(null, ustagramPostAuthorAvatarPath(post, "", emptyList()))
    }

    @Test
    fun postAuthorAvatarPathResolvesResidentByCharacterId() {
        val avatarPath = "/data/root.png"
        val root = residentWithAvatar(id = 1, name = "Root", avatarPath = avatarPath)
        val post = samplePost(
            authorKind = "resident",
            authorName = "Root",
            authorCharacterId = 1,
        )
        assertEquals(avatarPath, ustagramPostAuthorAvatarPath(post, null, listOf(root)))
    }

    @Test
    fun postAuthorAvatarPathFallsBackToNormalizedAuthorName() {
        val avatarPath = "/data/mira.png"
        val mira = residentWithAvatar(id = 2, name = "Mira · NPC", avatarPath = avatarPath)
        val post = samplePost(authorKind = "resident", authorName = "Mira", authorCharacterId = null)
        assertEquals(avatarPath, ustagramPostAuthorAvatarPath(post, null, listOf(mira)))
    }

    @Test
    fun postAuthorAvatarPathReturnsNullWhenNoStoredAvatar() {
        val root = ResidentCharacter(
            id = 1,
            name = "Root",
            persona = "Guide",
            cardJson = CharacterCardV2.buildCardJson("Root", CharacterProfileFields()),
        )
        val post = samplePost(authorKind = "resident", authorName = "Root", authorCharacterId = 1)
        assertEquals(null, ustagramPostAuthorAvatarPath(post, null, listOf(root)))
    }

    private fun samplePost(
        authorKind: String,
        authorName: String,
        authorCharacterId: Long? = null,
    ) = SocialPost(
        id = 1,
        kind = "moment",
        authorName = authorName,
        title = "",
        body = "Hi",
        createdAt = 0,
        mediaPath = null,
        mediaPrompt = null,
        mediaSeed = null,
        mediaStatus = "none",
        authorKind = authorKind,
        authorCharacterId = authorCharacterId,
    )

    private fun residentWithAvatar(id: Long, name: String, avatarPath: String) =
        ResidentCharacter(
            id = id,
            name = name,
            persona = "Resident",
            cardJson = CharacterCardV2.buildCardJson(
                name.removeSuffix(" · NPC"),
                CharacterProfileFields(avatarPath = avatarPath),
            ),
        )
}
