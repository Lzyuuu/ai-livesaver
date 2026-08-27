package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagingModelStoreTest {
    @Test
    fun catalogCoversElevenFilesWithValidChecksums() {
        assertEquals(11, ImagingModelStore.catalog.size)
        ImagingModelStore.catalog.forEach { entry ->
            assertTrue(entry.bytes > 0)
            assertTrue("${entry.file} sha 格式", Regex("[0-9a-f]{64}").matches(entry.sha256))
        }
        assertEquals(
            ImagingModelStore.catalog.sumOf { it.bytes },
            ImagingModelStore.TOTAL_BYTES,
        )
        // 权重与结构文件都齐：unet/clip/vae 编解码器/tokenizer。
        val names = ImagingModelStore.catalog.map { it.file }
        listOf(
            "unet.mnn", "unet.mnn.weight", "clip_v2.mnn", "clip_v2.mnn.weight",
            "vae_encoder.mnn", "vae_encoder.mnn.weight", "vae_decoder.mnn",
            "vae_decoder.mnn.weight", "token_emb.bin", "pos_emb.bin", "tokenizer.json",
        ).forEach { required -> assertTrue("缺文件 $required", names.contains(required)) }
    }

    @Test
    fun downloadBaseIsHttpsHuggingFaceResolve() {
        assertTrue(ImagingModelStore.URL_BASE.startsWith("https://huggingface.co/"))
        assertTrue(ImagingModelStore.URL_BASE.contains("/resolve/main/"))
        assertTrue(ImagingModelStore.URL_BASE.contains("CyberRealistic-LCM-Startup-model"))
    }

    @Test
    fun totalSizeIsAroundReferenceClaim() {
        // 参考按钮文案 "SD 1.5 · 1.3 GB"：官方文件合计 1313116830 B = 1252.3 MiB ≈ 1.3 GB。
        assertEquals(1313116830L, ImagingModelStore.TOTAL_BYTES)
        assertEquals(1313116830L, ImagingModelStore.catalog.sumOf { it.bytes })
    }
}
