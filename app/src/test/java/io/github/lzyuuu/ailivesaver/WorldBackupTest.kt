package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertFalse
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
}
