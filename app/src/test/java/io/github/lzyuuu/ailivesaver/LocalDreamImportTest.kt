package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LocalDreamImportTest {
    @Test
    fun avatarImportHasARecoverableSizeLimit() {
        assertTrue(avatarImportAllowed(1))
        assertTrue(avatarImportAllowed(10L * 1024 * 1024))
        assertFalse(avatarImportAllowed(0))
        assertFalse(avatarImportAllowed(10L * 1024 * 1024 + 1))
    }

    @Test
    fun readsJsonAndKeyValueLocalDreamParameters() {
        val json = parseLocalDreamParameters(
            """{"prompt":"rainy station","negative_prompt":"blurry","seed":42,"steps":24,"cfg_scale":6.5,"width":768,"height":512}""",
        )
        assertEquals("rainy station", json.prompt)
        assertEquals("blurry", json.negativePrompt)
        assertEquals(42L, json.seed)
        assertEquals(24, json.steps)
        assertEquals(6.5, json.cfg ?: -1.0, 0.0)
        assertEquals(768, json.width)
        assertEquals(512, json.height)

        val text = parseLocalDreamParameters(
            "prompt: quiet library\nnegative_prompt: text, watermark\nseed: 7\nscheduler: dpm++",
        )
        assertEquals("quiet library", text.prompt)
        assertEquals("text, watermark", text.negativePrompt)
        assertEquals(7L, text.seed)
        assertEquals("dpm++", text.scheduler)
    }

    @Test
    fun onlyStaleOwnedCacheNamesAreEligibleForCleanup() {
        val directory = Files.createTempDirectory("ai-livesaver-cache-").toFile()
        try {
            val staleImport = File(directory, "import-crashed").apply {
                writeText("temporary")
                setLastModified(1_000L)
            }
            val recentImport = File(directory, "import-active").apply {
                writeText("temporary")
                setLastModified(9_500L)
            }
            val staleOther = File(directory, "world.db").apply {
                writeText("keep")
                setLastModified(1_000L)
            }

            assertTrue(isStaleTemporaryCacheFile(staleImport, now = 10_000L, maxAgeMs = 5_000L))
            assertFalse(isStaleTemporaryCacheFile(recentImport, now = 10_000L, maxAgeMs = 5_000L))
            assertFalse(isStaleTemporaryCacheFile(staleOther, now = 10_000L, maxAgeMs = 5_000L))
        } finally {
            directory.deleteRecursively()
        }
    }
}
