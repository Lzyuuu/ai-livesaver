package io.github.lzyuuu.ailivesaver

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AuraRuntimeE2eTest {
    @Test
    fun runsFancyRuntimeWithoutActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelDir = File(context.filesDir, "models")
        val source = File(context.filesDir, "test-source.jpg")
        val target = File(context.filesDir, "test-target.jpg")
        assumeTrue(HfModelCatalog.all { File(modelDir, it.file).isFile } && source.isFile && target.isFile)
        val models = HfModelCatalog.associateBy { it.role }.mapValues { File(modelDir, it.value.file) }
        val useRestore = (context.getSystemService(android.app.ActivityManager::class.java)?.memoryClass ?: 0) >= 1024
        val loadError = if (useRestore) {
            MnnNative.nativeLoad(models.getValue("detector").absolutePath, models.getValue("embedding").absolutePath, models.getValue("swapper").absolutePath, models.getValue("restore").absolutePath, false)
        } else {
            MnnNative.nativeLoadWithoutRestore(models.getValue("detector").absolutePath, models.getValue("embedding").absolutePath, models.getValue("swapper").absolutePath)
        }
        assertEquals("", loadError)
        try {
            val result = AuraImagePipeline.run(BitmapFactory.decodeFile(source.absolutePath)!!, BitmapFactory.decodeFile(target.absolutePath)!!, useRestore)
            assertEquals(512, result.width)
            assertEquals(512, result.height)
        } finally {
            MnnNative.nativeUnload()
        }
    }
}
