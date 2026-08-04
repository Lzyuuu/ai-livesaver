package io.github.lzyuuu.ailivesaver

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Device success-path for Issue #28: Local Dream Test Connection + Generate.
 * Requires host mock on :8081 and `adb reverse tcp:8081 tcp:8081`.
 */
@RunWith(AndroidJUnit4::class)
class ImagingLocalDreamSuccessTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    // Grant before Activity launch so the notification dialog cannot keep MainActivity PAUSED.
    @get:Rule
    val ruleChain: TestRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuleChain
                .outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS))
                .around(composeRule)
        } else {
            composeRule
        }

    @Before
    fun seedDesktopShell() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // Belt-and-suspenders for recreate(): keep permission granted across activity restart.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation
                .executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
                .close()
        }
        writeWelcomeGuideCompleted(context, true)
        WorldStore(context).use { store ->
            DesktopSeed.ensureDesktopWorld(store, userName = "焰宇", about = "imaging-p0")
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun testConnectionAndGeneratePersistsResult() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val localDreamAvailable = runCatching {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("127.0.0.1", 8081), 500)
            }
            true
        }.getOrDefault(false)
        assumeTrue("Requires host Local Dream mock on :8081 and adb reverse tcp:8081 tcp:8081", localDreamAvailable)
        val outDir = File(context.filesDir, "issue-28-p0").apply { mkdirs() }

        composeRule.onNodeWithTag("desktop-dock-imaging").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("imaging-studio").assertIsDisplayed()

        composeRule.onNodeWithTag("imaging-backend-local_dream").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("imaging-test-connection").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithText("Connected to Local Dream", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        saveScreenshot(File(outDir, "instrument-local-dream-connected.png"))

        composeRule.onNodeWithTag("imaging-prompt").performScrollTo().assertIsDisplayed().performTextInput("a golden lantern at dusk")
        composeRule.waitForIdle()
        // Hide IME so Generate is not covered and layout settles before click.
        hideIme()
        composeRule.onNodeWithTag("imaging-generate")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Local Dream generation ready", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("imaging-preview").assertIsDisplayed()
        hideIme()
        saveScreenshot(File(outDir, "instrument-local-dream-generated.png"))
        // Also publish under issue-28-final path for the final visual pass.
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("mkdir -p /sdcard/Download/issue-28-final")
            .close()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(
                "cp /sdcard/Download/issue-28-p0/instrument-local-dream-connected.png " +
                    "/sdcard/Download/issue-28-final/current-local-dream-connected.png",
            )
            .close()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(
                "cp /sdcard/Download/issue-28-p0/instrument-local-dream-generated.png " +
                    "/sdcard/Download/issue-28-final/current-local-dream-generated.png",
            )
            .close()
        Thread.sleep(200)

        val posts = WorldStore(context).use { it.posts("moment") }
        assertTrue(
            "Expected Imaging Studio imported media post",
            posts.any { it.body.contains("Imaging Studio") && !it.mediaPath.isNullOrBlank() },
        )
    }

    private fun hideIme() {
        // ESCAPE only — BACK can leave Imaging Studio if the IME is already closed.
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent 111")
            .close()
        Thread.sleep(250)
        composeRule.waitForIdle()
    }

    private fun saveScreenshot(target: File) {
        // Shell screencap survives app uninstall and is pullable without run-as.
        val publicPath = "/sdcard/Download/issue-28-p0/${target.name}"
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/issue-28-p0").close()
        uiAutomation.executeShellCommand("screencap -p $publicPath").close()
        // Also keep private copy when possible.
        runCatching {
            val bitmap = uiAutomation.takeScreenshot() ?: return@runCatching
            target.parentFile?.mkdirs()
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
        }
        // Brief settle so screencap finishes writing.
        Thread.sleep(300)
    }
}
