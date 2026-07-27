package io.github.lzyuuu.ailivesaver

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WorldMediaCleanupSmokeTest {
    @Test
    fun removesOnlyUnreferencedPrivateMedia() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val media = File(context.filesDir, "media").apply { mkdirs() }
        val referenced = File(media, "cleanup-referenced.png").apply { writeBytes(byteArrayOf(1)) }
        val orphan = File(media, "cleanup-orphan.png").apply { writeBytes(byteArrayOf(2)) }
        val postId = WorldStore(context).use {
            it.createImportedMediaPost(
                body = "cleanup smoke test",
                path = referenced.absolutePath,
                description = "referenced media",
                audience = "world",
                audienceCharacterIds = "",
                aiResponsesEnabled = false,
            )
        }
        try {
            WorldStore(context).use { it.pruneOrphanMedia() }
            assertTrue(referenced.isFile)
            assertFalse(orphan.exists())
        } finally {
            WorldStore(context).use { it.deleteUserPost(postId) }
            referenced.delete()
            orphan.delete()
        }
    }
}
