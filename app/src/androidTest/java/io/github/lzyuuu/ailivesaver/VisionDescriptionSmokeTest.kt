package io.github.lzyuuu.ailivesaver

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisionDescriptionSmokeTest {
    @Test
    fun appliesVisionResultOnlyToTheSameUndescribedImage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val path = "${context.cacheDir}/vision-current.jpg"
        val postId = WorldStore(context).use {
            it.createImportedMediaPost(
                body = "",
                path = path,
                description = "",
                audience = "world",
                audienceCharacterIds = "",
                aiResponsesEnabled = false,
            )
        }

        try {
            WorldStore(context).use { store ->
                assertFalse(
                    store.applyVisionDescription(
                        postId,
                        "${context.cacheDir}/vision-replaced.jpg",
                        "wrong image",
                    ),
                )
                assertTrue(store.applyVisionDescription(postId, path, "current image"))
                assertFalse(store.applyVisionDescription(postId, path, "late overwrite"))
                assertEquals(
                    "current image",
                    store.posts("moment").first { it.id == postId }.mediaDescription,
                )
            }
        } finally {
            WorldStore(context).use { it.deleteUserPost(postId) }
        }
    }
}
