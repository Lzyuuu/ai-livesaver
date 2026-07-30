package io.github.lzyuuu.ailivesaver

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderEnvSeedTest {
    @Test
    fun seedDesktopShellForLiveVerification() {
        seedDesktopShellForSmoke(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
    }

    @Test
    fun seedChatProviderFromInstrumentationArgs() {
        val args = InstrumentationRegistry.getArguments()
        val baseUrl = args.getString("baseUrl") ?: return
        val model = args.getString("model") ?: return
        val apiKey = args.getString("apiKey") ?: return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ProviderStore(context).save(
            ProviderConfig(
                preset = ProviderPreset.Custom,
                baseUrl = baseUrl,
                model = model,
                apiKey = apiKey,
            ),
        )
        assertTrue(ProviderStore(context).loadFor(ProviderTask.Chat).isValid())
        waitForSharedPrefsFile(context, "provider")
    }

    private fun waitForSharedPrefsFile(context: Context, name: String) {
        val file = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")
        val deadline = System.currentTimeMillis() + 5_000
        while (!file.exists() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertTrue(file.exists())
    }
}
