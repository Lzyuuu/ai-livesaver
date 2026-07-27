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
}
