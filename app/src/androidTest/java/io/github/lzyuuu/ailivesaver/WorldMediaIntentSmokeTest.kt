package io.github.lzyuuu.ailivesaver

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorldMediaIntentSmokeTest {
    @Test
    fun rollsBackMediaPublicationWhenItsWorldEventCannotBeCommitted() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val path = File(context.filesDir, "media/generated-atomic-smoke.png").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val store = WorldStore(context)
        val postId = store.createMediaPost("atomic publication", "test prompt")
        val job = store.mediaJobs().first { it.postId == postId }

        try {
            store.writableDatabase.execSQL(
                """
                CREATE TEMP TRIGGER reject_test_world_event
                BEFORE INSERT ON world_events
                BEGIN
                    SELECT RAISE(ABORT, 'test rejection');
                END
                """.trimIndent(),
            )
            assertTrue(
                runCatching {
                    store.markMediaReady(postId, job.revision, path.absolutePath, 7L)
                }.isFailure,
            )
            assertEquals("pending", store.mediaJobs().first { it.postId == postId }.status)
            assertTrue(store.posts("moment").none { it.id == postId })
            assertTrue(store.mediaVersions(postId).isEmpty())
        } finally {
            runCatching {
                store.writableDatabase.execSQL("DROP TRIGGER IF EXISTS reject_test_world_event")
            }
            store.deleteUserPost(postId)
            store.close()
            path.delete()
        }
    }

    @Test
    fun ignoresACompletedImageFromASupersededRedraw() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val oldPath = File(context.filesDir, "media/generated-stale-smoke.png").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val newPath = File(context.filesDir, "media/generated-latest-smoke.png").apply {
            writeBytes(byteArrayOf(4, 5, 6))
        }
        val store = WorldStore(context)
        val postId = store.createMediaPost("redraw race", "same prompt")

        try {
            val oldJob = store.mediaJobs().first { it.postId == postId }
            store.prepareRedraw(postId, "same prompt")
            val newJob = store.mediaJobs().first { it.postId == postId }

            assertTrue(newJob.revision > oldJob.revision)
            assertFalse(store.markMediaFailed(postId, oldJob.revision, "stale failure"))
            assertFalse(store.markMediaWaiting(postId, oldJob.revision, "stale waiting"))
            assertFalse(
                store.markMediaReady(postId, oldJob.revision, oldPath.absolutePath, 11L),
            )
            assertEquals("pending", store.mediaJobs().first { it.postId == postId }.status)
            assertTrue(store.mediaVersions(postId).isEmpty())
            assertTrue(
                store.markMediaReady(postId, newJob.revision, newPath.absolutePath, 12L),
            )
            assertEquals(
                newPath.absolutePath,
                store.posts("moment").first { it.id == postId }.mediaPath,
            )
        } finally {
            store.deleteUserPost(postId)
            store.close()
            oldPath.delete()
            newPath.delete()
        }
    }

    @Test
    fun keepsPausedMediaJobsPendingForRetry() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val postId = WorldStore(context).use {
            it.createMediaPost("waiting for storage", "retryable prompt")
        }

        try {
            WorldStore(context).use { store ->
                val job = store.mediaJobs().first { it.postId == postId }
                store.markMediaWaiting(postId, job.revision, "存储空间不足，生成已暂停")
                assertEquals(postId, store.nextPendingMediaJob()?.postId)
                assertEquals(
                    "pending",
                    store.mediaJobs().first { it.postId == postId }.status,
                )
            }
        } finally {
            WorldStore(context).use { it.deleteUserPost(postId) }
        }
    }

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
            WorldStore(context).use {
                val job = it.mediaJobs().first { queued -> queued.postId == postId }
                it.markMediaReady(postId, job.revision, path.absolutePath, 123L)
            }
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
                    val job = store.mediaJobs().first { it.postId == postId }
                    store.markMediaReady(postId, job.revision, secondPath.absolutePath, 456L)
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
