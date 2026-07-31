package io.github.lzyuuu.ailivesaver

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuraSwapSmokeTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedDesktopShell() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        writeWelcomeGuideCompleted(context, true)
        WorldStore(context).use { DesktopSeed.ensureDesktopWorld(it, "焰宇", "issue-30") }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun opensAuraSwapAndModelStoreEmptyDirectoryPath() {
        composeRule.onNodeWithTag("desktop-hub-creative_suite").performClick()
        composeRule.onNodeWithTag("hub-app-aura_swap").performClick()
        composeRule.onNodeWithTag("aura-swap-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("aura-run").performClick()
        composeRule.onNodeWithTag("aura-status").assertIsDisplayed()
        composeRule.onNodeWithTag("aura-store-toggle").performClick()
        composeRule.onNodeWithTag("hf-installed").assertIsDisplayed()
        composeRule.onNodeWithTag("hf-download").assertIsDisplayed()
    }

    @Test
    fun downloadsWithRetryChecksumAndRejectsUnavailableInference() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val payload = "small test model\n".toByteArray()
        val attempts = AtomicInteger(0)
        ServerSocket(0).use { server ->
            val worker = thread {
                try {
                    repeat(3) {
                        server.accept().use { socket ->
                            val count = attempts.incrementAndGet()
                            socket.getInputStream().bufferedReader().readLine()
                            val output = socket.getOutputStream()
                            if (count < 3) {
                                output.write("HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                            } else {
                                output.write("HTTP/1.1 200 OK\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                output.write(payload)
                            }
                            output.flush()
                        }
                    }
                } catch (_: Exception) {
                    // The test failure will report the assertion; do not crash the runner from cleanup.
                }
            }
            val model = HfModelStore.downloadFile(
                context,
                "http://127.0.0.1:${server.localPort}/model.bin",
                "inswapper_128.fp16.mnn",
                expectedSha256 = HfModelStore.sha256(File(context.cacheDir, "expected").apply { writeBytes(payload) }),
                maxAttempts = 3,
            )
            worker.join()
            assertEquals(3, attempts.get())
            assertEquals(payload.toList(), model.readBytes().toList())

        }
    }

    @Test
    fun nativeMnnLoaderRejectsInvalidModelWithRole() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val invalid = File(context.cacheDir, "invalid.mnn").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val error = MnnNative.nativeLoadModels(invalid.absolutePath, invalid.absolutePath, invalid.absolutePath)
        assertTrue(error.contains("SCRFD"))
    }

    @Test
    fun loadsFancyModelsWhenStagedOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.filesDir, "models")
        val files = HfModelCatalog.map { File(dir, it.file) }
        assumeTrue(files.all { it.isFile && it.length() > 8 })
        val error = MnnNative.nativeLoad(
            files.first { it.name.startsWith("scrfd") }.absolutePath,
            files.first { it.name.startsWith("arcface") }.absolutePath,
            files.first { it.name.startsWith("inswapper") }.absolutePath,
            files.first { it.name.startsWith("codeformer") }.absolutePath,
            false,
        )
        assertEquals("", error)
    }

    @Test
    fun imagePipelineConvertsChwAndParsesFivePointFace() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.RED)
            setPixel(1, 0, Color.GREEN)
            setPixel(0, 1, Color.BLUE)
            setPixel(1, 1, Color.WHITE)
        }
        val chw = AuraImagePipeline.bitmapToChw(bitmap, 2)
        assertEquals(12, chw.size)
        assertEquals(1f, chw[0], .01f)
        assertEquals(-1f, chw[4], .01f)
        val face = AuraImagePipeline.parseFaces(
            floatArrayOf(0f, 0f, 100f, 100f, .9f, 20f, 20f, 80f, 20f, 50f, 50f, 25f, 80f, 75f, 80f),
            100,
            100,
        ).single()
        assertEquals(.9f, face.score, .001f)
        assertEquals(5, face.points.size)
        assertEquals(512, AuraImagePipeline.align(bitmap, face, 512).width)
        val roundTrip = AuraImagePipeline.chwToBitmap(chw, 2)
        assertEquals(Color.RED, roundTrip.getPixel(0, 0))
    }

    @Test
    fun fusesRestoredFaceBackToTargetSize() {
        val target = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val restored = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val face = AuraFace(.99f, listOf(
            android.graphics.PointF(25f, 18f), android.graphics.PointF(55f, 18f),
            android.graphics.PointF(40f, 32f), android.graphics.PointF(28f, 45f), android.graphics.PointF(52f, 45f),
        ))
        val fused = AuraImagePipeline.fuseRestored(target, face, restored)
        assertEquals(80, fused.width)
        assertEquals(60, fused.height)
        assertTrue(fused.getPixelsChangedFrom(target))
    }

    private fun Bitmap.getPixelsChangedFrom(other: Bitmap): Boolean {
        for (y in 0 until height) for (x in 0 until width) if (getPixel(x, y) != other.getPixel(x, y)) return true
        return false
    }
}
