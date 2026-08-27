package io.github.lzyuuu.ailivesaver

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPagesDataSmokeTest {
    private fun freshStore(): WorldStore {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "smoke-settings-${System.nanoTime()}.db"
        return WorldStore(context, dbName)
    }

    @Test
    fun usernameRoundTripsAndStripsAtPrefix() {
        freshStore().use { store ->
            store.createWorld("User", "Root", "persona")
            assertEquals("", store.identity().username)
            store.updateIdentity("User", "", "bio", username = "@zhaoyu")
            assertEquals("zhaoyu", store.identity().username)
            store.updateIdentity("User", "", "bio", username = "plain")
            assertEquals("plain", store.identity().username)
        }
    }

    @Test
    fun duplicatePostCleanupKeepsOldest() {
        freshStore().use { store ->
            store.createWorld("User", "Root", "persona")
            repeat(3) { index ->
                store.createPost(
                    kind = Y_POST_KIND,
                    authorName = "User",
                    title = "",
                    body = "duplicate body $index",
                    authorKind = "user",
                    audience = "world",
                    audienceCharacterIds = "",
                    worldEventKind = Y_POST_KIND,
                )
            }
            // 相同内容视为重复：三条同文再插两条。
            repeat(2) {
                store.createPost(
                    kind = Y_POST_KIND,
                    authorName = "User",
                    title = "",
                    body = "duplicate body 0",
                    authorKind = "user",
                    audience = "world",
                    audienceCharacterIds = "",
                    worldEventKind = Y_POST_KIND,
                )
            }
            assertEquals(2, store.countDuplicatePosts())
            val removed = store.deleteDuplicatePosts()
            assertEquals(2, removed)
            assertEquals(0, store.countDuplicatePosts())
            assertEquals(3, store.posts("y").count { it.authorKind == "user" })
        }
    }

    @Test
    fun cleanStoreReportsNoCleanupTargets() {
        freshStore().use { store ->
            store.createWorld("User", "Root", "persona")
            assertEquals(0, store.countOrphanMessages())
            assertEquals(0, store.countDriedMemories())
            assertEquals(0, store.countDuplicatePosts())
            assertTrue(store.identity().username.isBlank())
        }
    }
}
