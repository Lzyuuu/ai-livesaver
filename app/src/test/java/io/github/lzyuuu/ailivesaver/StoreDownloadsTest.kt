package io.github.lzyuuu.ailivesaver

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 商店真实下载：产品 → 资产映射、镜像 URL、打开路由修正。 */
class StoreDownloadsTest {

    /** 直接驱动真实资产文件，保证测试的就是即将发布在 APK 里的目录。 */
    private fun loadShippedCatalog(): String {
        val root = System.getProperty("user.dir")
        val candidates = listOf(
            "$root/app/src/main/assets/fancy_store.json",
            "$root/src/main/assets/fancy_store.json",
        )
        val path = candidates.firstOrNull { Files.exists(Paths.get(it)) }
            ?: error("找不到 fancy_store.json，尝试过: $candidates")
        return String(Files.readAllBytes(Paths.get(path)), Charsets.UTF_8)
    }

    @Test
    fun hdUpscalersMapsToBothVerifiedRealEsrganModels() {
        val items = storeDownloadItems("hd-upscalers")
        assertEquals(UpscalerModelStore.catalog.map { it.file }, items.map { it.fileName })
        assertEquals(
            UpscalerModelStore.catalog.map { it.sha256 }.toSet(),
            items.map { it.sha256 }.toSet(),
        )
        // 镜像 base + 完整 https URL。
        assertTrue(items.all { it.url == "${UpscalerModelStore.URL_BASE}/${it.fileName}" })
        assertTrue(items.all { it.url.startsWith("https://hf-mirror.com/") })
    }

    @Test
    fun auraSwapMapsToFourCatalogModelsOnMirror() {
        val items = storeDownloadItems("aura_swap")
        assertEquals(HfModelCatalog.map { it.file }, items.map { it.fileName })
        assertEquals(HfModelCatalog.map { it.sha256 }, items.map { it.sha256 })
        // 目录 URL 是 huggingface.co 直连；商店下载统一走 hf-mirror 镜像，SHA-256 不变。
        assertTrue(items.all { it.url.startsWith("https://hf-mirror.com/Mr-J-369/Fancy-AI/") })
        assertTrue(items.all { it.sha256.matches(Regex("[0-9a-f]{64}")) })
    }

    @Test
    fun plainAppsHaveNoDownloadAssets() {
        assertEquals(emptyList<StoreDownloadItem>(), storeDownloadItems("y"))
        assertEquals(emptyList<StoreDownloadItem>(), storeDownloadItems("benchmark"))
        assertEquals(emptyList<StoreDownloadItem>(), storeDownloadItems("no-such-product"))
    }

    @Test
    fun shippedPackageLaunchTargetsResolveToDesktopApps() {
        val products = FancyStoreCatalogParser.parse(loadShippedCatalog())
        // package 打开路由不再回退 Settings：hd-upscalers/face-enhance → Imaging，memory-pack → Settings(显式)。
        val expected = mapOf(
            "hd-upscalers" to DesktopApp.Imaging,
            "aura_swap" to DesktopApp.AuraSwap,
            "memory-pack" to DesktopApp.Settings,
        )
        expected.forEach { (id, app) ->
            val product = products.first { it.id == id }
            assertEquals("$id launchTarget", app, storeAppForProduct(product))
        }
    }
}
