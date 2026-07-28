package io.github.lzyuuu.ailivesaver

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorldRestoreRecoverySmokeTest {
    @Test
    fun restoresSnapshotLeftByInterruptedRestore() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val originalId = WorldStore(context).use { store ->
            store.addCharacter(
                name = "Restore original ${System.nanoTime()}",
                persona = "Original",
                attentionTier = "resident",
                appearance = "",
                clothing = "",
                negativePrompt = "",
            ).also { store.checkpointForBackup() }
        }
        val rollback = File(context.cacheDir, "rollback-test-${System.nanoTime()}").apply {
            mkdirs()
        }
        val database = context.getDatabasePath("world.db")
        database.copyTo(File(rollback, "world.db"))
        File(rollback, "state.json").writeText(
            JSONObject()
                .put("hadDatabase", true)
                .put("hadMedia", File(context.filesDir, "media").exists())
                .put("worldSettings", JSONObject(WorldEngine.backupSettings(context)))
                .toString(),
        )
        File(rollback, "snapshot-ready").writeText("")
        val replacementId = WorldStore(context).use { store ->
            store.addCharacter(
                name = "Restore replacement ${System.nanoTime()}",
                persona = "Replacement",
                attentionTier = "resident",
                appearance = "",
                clothing = "",
                negativePrompt = "",
            ).also { store.checkpointForBackup() }
        }

        try {
            assertTrue(WorldBackup.recoverInterruptedRestore(context, force = true))
            WorldStore(context).use { store ->
                val ids = store.characters().map(ResidentCharacter::id)
                assertTrue(originalId in ids)
                assertFalse(replacementId in ids)
            }
        } finally {
            WorldStore(context).use {
                it.deleteCharacter(originalId)
                it.deleteCharacter(replacementId)
            }
            rollback.deleteRecursively()
        }
    }
}
