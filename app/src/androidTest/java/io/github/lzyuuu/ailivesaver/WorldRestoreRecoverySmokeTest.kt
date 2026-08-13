package io.github.lzyuuu.ailivesaver

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorldRestoreRecoverySmokeTest {
    @Test
    fun exportRestoreRoundtripRestoresAppInstall() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val zip = File(context.filesDir, "app-install-roundtrip-${System.nanoTime()}.zip")
        val uri = Uri.fromFile(zip)
        val now = System.currentTimeMillis()
        try {
            val store = WorldStore(context)
            store.saveAppInstall(
                PersistedAppInstall("y", InstallStatus.INSTALLED, now, true, 3, "1.0", now),
            )

            val exported = CountDownLatch(1)
            var exportResult: Result<Unit>? = null
            WorldBackup.export(context, store, uri, includeMedia = false) {
                exportResult = it
                exported.countDown()
            }
            assertTrue("export timed out", exported.await(20, TimeUnit.SECONDS))
            exportResult!!.getOrThrow()
            assertTrue(zip.isFile && zip.length() > 0)

            assertTrue(store.deleteAppInstall("y"))
            assertNull(store.loadAppInstall("y"))

            val restored = CountDownLatch(1)
            var restoreResult: Result<Unit>? = null
            WorldBackup.restore(context, store, uri) {
                restoreResult = it
                restored.countDown()
            }
            assertTrue("restore timed out", restored.await(30, TimeUnit.SECONDS))
            restoreResult!!.getOrThrow()

            WorldStore(context).use { fresh ->
                val y = fresh.loadAppInstall("y")
                assertTrue(y != null)
                assertEquals(InstallStatus.INSTALLED, y!!.status)
                assertTrue(y.onHome)
                assertEquals(3, y.homeOrder)
            }
        } finally {
            zip.delete()
            WorldStore(context).use { it.deleteAppInstall("y") }
        }
    }

    @Test
    fun backupSnapshotIncludesAppInstallRows() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val snapshot = File(context.cacheDir, "store-backup-${System.nanoTime()}.db")
        val now = System.currentTimeMillis()
        WorldStore(context).use { store ->
            store.saveAppInstall(
                PersistedAppInstall("y", InstallStatus.INSTALLED, now, true, 1, "1.0", now),
            )
            store.copyDatabaseForBackup(snapshot)
        }
        try {
            SQLiteDatabase.openDatabase(snapshot.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
                database.rawQuery(
                    "SELECT status, on_home FROM app_install WHERE app_id = ?",
                    arrayOf("y"),
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("INSTALLED", cursor.getString(0))
                    assertEquals(1, cursor.getInt(1))
                }
            }
        } finally {
            WorldStore(context).use { it.deleteAppInstall("y") }
            snapshot.delete()
        }
    }

    @Test
    fun copiesAReadableDatabaseSnapshotWhileTheStoreIsOpen() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "Backup snapshot ${System.nanoTime()}"
        val snapshot = File(context.cacheDir, "backup-snapshot-${System.nanoTime()}.db")
        val characterId = WorldStore(context).use { store ->
            store.addCharacter(
                name = name,
                persona = "Snapshot",
                attentionTier = "resident",
                appearance = "",
                clothing = "",
                negativePrompt = "",
            ).also { store.copyDatabaseForBackup(snapshot) }
        }

        try {
            val copiedName = SQLiteDatabase.openDatabase(
                snapshot.path,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { database ->
                database.rawQuery(
                    "SELECT name FROM characters WHERE id = ?",
                    arrayOf(characterId.toString()),
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }
            assertTrue(copiedName == name)
        } finally {
            WorldStore(context).use {
                it.writableDatabase.execSQL(
                    "UPDATE characters SET active = 0 WHERE id = ?",
                    arrayOf(characterId),
                )
                it.deleteCharacter(characterId)
            }
            snapshot.delete()
        }
    }

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
                it.writableDatabase.execSQL(
                    "UPDATE characters SET active = 0 WHERE id IN (?, ?)",
                    arrayOf(originalId, replacementId),
                )
                it.deleteCharacter(originalId)
                it.deleteCharacter(replacementId)
            }
            rollback.deleteRecursively()
        }
    }
}
