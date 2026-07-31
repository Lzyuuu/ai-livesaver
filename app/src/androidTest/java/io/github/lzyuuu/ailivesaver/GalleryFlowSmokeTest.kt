package io.github.lzyuuu.ailivesaver

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryFlowSmokeTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private var postId = 0L

    @Before
    fun seedGallery() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        seedDesktopShellForSmoke(context)
        clearMomentPostsForSmoke(context)
        WorldStore(context).use { store ->
            val character = store.characters(false).first()
            val image = File(context.filesDir, "media/gallery-smoke.png")
            image.parentFile?.mkdirs()
            FileOutputStream(image).use { output ->
                Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.rgb(198, 161, 91))
                    compress(Bitmap.CompressFormat.PNG, 100, output)
                    recycle()
                }
            }
            postId = store.createImportedMediaPost(
                body = "gallery smoke",
                path = image.absolutePath,
                description = "smoke image",
                audience = "world",
                audienceCharacterIds = character.id.toString(),
                aiResponsesEnabled = false,
            )
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun browseByCharacterAndOpenImage() {
        composeRule.onNodeWithTag("desktop-dock-gallery", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("gallery-screen", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("gallery-filter-all", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("gallery-image-$postId", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("gallery-viewer", useUnmergedTree = true).assertIsDisplayed()
    }
}
