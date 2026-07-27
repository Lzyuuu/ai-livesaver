package io.github.lzyuuu.ailivesaver

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorldMediaIntentSmokeTest {
    @Test
    fun hidesGeneratedPostUntilReadyAndKeepsProviderProvenance() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val path = File(context.filesDir, "media/generated-intent-smoke.png").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val postId = WorldStore(context).use { store ->
            store.createPost(
                kind = "moment",
                authorName = "Mira",
                title = "",
                body = "An evening walk",
                authorKind = "resident",
                providerName = "DeepSeek",
                modelName = "test-model",
                mediaPrompt = "Mira, blue jacket, evening street",
                mediaNegativePrompt = "blurry",
            )
        }
        try {
            assertTrue(
                WorldStore(context).use { store ->
                    store.posts("moment").none { it.id == postId }
                },
            )
            WorldStore(context).use { it.markMediaReady(postId, path.absolutePath, 123L) }
            val visible = WorldStore(context).use { store ->
                store.posts("moment").first { it.id == postId }
            }
            assertEquals("ready", visible.mediaStatus)
            assertEquals("local_dream", visible.mediaSource)
            assertEquals("DeepSeek", visible.providerName)
            assertEquals("test-model", visible.modelName)
            val event = WorldStore(context).use { store ->
                store.worldEvents(limit = 100).first { it.sourcePostId == postId }
            }
            assertEquals("DeepSeek", event.providerName)
            assertEquals("test-model", event.modelName)
            val secondPath = File(context.filesDir, "media/generated-intent-smoke-redraw.png").apply {
                writeBytes(byteArrayOf(4, 5, 6))
            }
            try {
                val eventCount = WorldStore(context).use { store ->
                    store.worldEvents(limit = 100).count { it.sourcePostId == postId }
                }
                WorldStore(context).use { store ->
                    store.prepareRedraw(postId, "Mira, red coat, rainy street")
                    val visibleDuringRedraw = store.posts("moment").first { it.id == postId }
                    assertEquals("pending", visibleDuringRedraw.mediaStatus)
                    assertEquals(path.absolutePath, visibleDuringRedraw.mediaPath)
                    store.markMediaReady(postId, secondPath.absolutePath, 456L)
                }
                assertEquals(
                    eventCount,
                    WorldStore(context).use { store ->
                        store.worldEvents(limit = 100).count { it.sourcePostId == postId }
                    },
                )
                assertEquals(
                    2,
                    WorldStore(context).use { store -> store.mediaVersions(postId).size },
                )
            } finally {
                secondPath.delete()
            }
        } finally {
            WorldStore(context).use { it.deleteAiPost(postId) }
            path.delete()
        }
    }
}
