package io.github.lzyuuu.ailivesaver

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorldDataErasureSmokeTest {
    @Test
    fun erasesWorldCredentialsPreferencesDiagnosticsAndCache() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = WorldStore(context)
        store.addCharacter(
            name = "Erase test ${System.nanoTime()}",
            persona = "Temporary",
            attentionTier = "resident",
            appearance = "",
            clothing = "",
            negativePrompt = "",
        )
        File(context.filesDir, "media/erase-test.jpg").apply {
            parentFile?.mkdirs()
            writeText("media")
        }
        val cacheFile = File(context.cacheDir, "erase-test.tmp").apply { writeText("cache") }
        context.getSharedPreferences(APP_PREFERENCES, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("erase_test", "present")
            .commit()
        ProviderStore(context).save(
            ProviderConfig(
                baseUrl = "https://example.com/v1",
                model = "erase-test",
                apiKey = "secret",
            ),
        )
        LocalDreamStatsStore.save(
            context,
            LocalDreamRunStats(1_000, 100, 512, 512, System.currentTimeMillis()),
        )
        val now = System.currentTimeMillis()
        store.saveAppInstall(
            PersistedAppInstall("y", InstallStatus.INSTALLED, now, true, 1, "1.0", now),
        )
        assertEquals(InstallStatus.INSTALLED, store.loadAppInstall("y")!!.status)

        val finished = CountDownLatch(1)
        var result: Result<Unit>? = null
        WorldBackup.eraseAll(context, store) {
            result = it
            finished.countDown()
        }

        assertTrue(finished.await(10, TimeUnit.SECONDS))
        result!!.getOrThrow()
        assertFalse(context.getDatabasePath("world.db").exists())
        assertFalse(File(context.filesDir, "media").exists())
        assertFalse(cacheFile.exists())
        assertTrue(
            context.getSharedPreferences(APP_PREFERENCES, android.content.Context.MODE_PRIVATE)
                .all
                .isEmpty(),
        )
        assertTrue(ProviderStore(context).load().apiKey.isEmpty())
        assertNull(LocalDreamStatsStore.load(context))
        WorldStore(context).use { fresh ->
            assertEquals(0, fresh.countAppInstalls())
            assertNull(fresh.loadAppInstall("y"))
        }
    }
}
