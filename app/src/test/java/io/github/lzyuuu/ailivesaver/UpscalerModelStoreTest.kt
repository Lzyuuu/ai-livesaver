package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpscalerModelStoreTest {
    @Test
    fun catalogCoversPhotoAndAnimeModels() {
        assertEquals(2, UpscalerModelStore.catalog.size)
        val names = UpscalerModelStore.catalog.map { it.file }
        assertTrue(names.contains("realesrgan_x4plus.fp16.mnn"))
        assertTrue(names.contains("realesrgan_x4plus_anime.fp16.mnn"))
        assertEquals(
            UpscalerModelStore.catalog.sumOf { it.bytes },
            UpscalerModelStore.TOTAL_BYTES,
        )
        assertEquals(42638892L, UpscalerModelStore.TOTAL_BYTES)
    }

    @Test
    fun downloadBaseIsHttpsMirrorResolve() {
        assertTrue(UpscalerModelStore.URL_BASE.startsWith("https://hf-mirror.com/"))
        assertTrue(UpscalerModelStore.URL_BASE.contains("/resolve/main"))
        UpscalerModelStore.catalog.forEach { entry ->
            assertTrue("${entry.id} sha", Regex("[0-9a-f]{64}").matches(entry.sha256))
            assertTrue("${entry.id} url", entry.file.endsWith(".fp16.mnn"))
        }
    }
}
