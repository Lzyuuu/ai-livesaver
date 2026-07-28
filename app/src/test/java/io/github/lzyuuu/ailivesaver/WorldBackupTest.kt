package io.github.lzyuuu.ailivesaver

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldBackupTest {
    @Test
    fun onlyAllowsExpectedArchiveEntries() {
        assertTrue(WorldBackup.safeEntry("world.db"))
        assertTrue(WorldBackup.safeEntry("media/image.png"))
        assertFalse(WorldBackup.safeEntry("../world.db"))
        assertFalse(WorldBackup.safeEntry("media/../secret"))
    }

    @Test
    fun rejectsUnversionedAndFutureDatabasesBeforeRestore() {
        assertTrue(WorldBackup.supportsDatabaseVersion(WORLD_DATABASE_VERSION))
        assertFalse(WorldBackup.supportsDatabaseVersion(0))
        assertFalse(WorldBackup.supportsDatabaseVersion(WORLD_DATABASE_VERSION + 1))
    }

    @Test
    fun rewritesRestoredMediaPathsOnlyWhenTheFileExists() {
        val root = Files.createTempDirectory("world-backup-").toFile()
        val source = File(root, "source").apply { mkdirs() }
        val destination = File(root, "destination")
        File(source, "user-avatar.jpg").writeText("avatar")

        try {
            assertEquals(
                File(destination, "user-avatar.jpg").path,
                WorldBackup.normalizedMediaPath(
                    "/old/device/user-avatar.jpg",
                    source,
                    destination,
                ),
            )
            assertNull(
                WorldBackup.normalizedMediaPath(
                    "/old/device/missing.jpg",
                    source,
                    destination,
                ),
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
