package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

            val source = File(context.cacheDir, "source.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val target = File(context.cacheDir, "target.png").apply { writeBytes(byteArrayOf(4, 5, 6)) }
            val detector = File(context.cacheDir, "scrfd_10g.fp16.mnn").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val embedding = File(context.cacheDir, "arcface_w600k_r50.fp16.mnn").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val error = runCatching {
                MnnAuraBackend.run(context, AuraSwapRequest(source, target, model, detector, embedding))
            }.exceptionOrNull()
            assertTrue(error?.message.orEmpty().contains("真实 Aura 推理未启用"))
        }
    }
}
