package io.github.lzyuuu.ailivesaver

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FancyStoreCatalogTest {

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
    fun shippedCatalogParsesSeventeenProductsIntoThreeBuckets() {
        val products = FancyStoreCatalogParser.parse(loadShippedCatalog())
        assertEquals(17, products.size)

        // 目录扩回参考 V4.51 的规模：15 可用（参考 14 + 我们多保留的 aura-swap），
        // 仅参考没有的两个差异化包保持 upcoming。
        val buckets = StoreCatalogBuckets.from(products)
        assertEquals(15, buckets.available.size)
        assertEquals(0, buckets.comingSoon.size)
        assertEquals(2, buckets.upcoming.size)

        assertEquals(
            setOf(
                "y", "ustagram", "rebbit", "binder", "games", "phone", "groups",
                "root-creator", "aura", "aura-swap", "hd-upscalers", "benchmark",
                "storage", "lorebook", "root-producer",
            ),
            buckets.available.map { it.id }.toSet(),
        )
        assertTrue(buckets.upcoming.any { it.id == "face-enhance" && it.kind == "package" })
        assertTrue(buckets.upcoming.any { it.id == "memory-pack" && it.kind == "package" })
    }

    @Test
    fun shippedCatalogCarriesReferenceFacts() {
        val products = FancyStoreCatalogParser.parse(loadShippedCatalog())
        val auraSwap = products.first { it.id == "aura-swap" }
        assertEquals(561837652L, auraSwap.requiredDownloadBytes)
        assertTrue(auraSwap.requirements.any { it.contains("6 GB") })

        // 参考原则（仓库既定）：入口开放、无 Pro 墙——游戏可获取；详情保留参考的
        // "Fancy AI Pro" 描述文案，但不再构成获取门槛。
        val games = products.first { it.id == "games" }
        assertEquals(StoreAvailability.AVAILABLE, games.availability)

        val producer = products.first { it.id == "root-producer" }
        assertTrue(producer.featured)
        assertEquals(StoreAvailability.AVAILABLE, producer.availability)

        // Packages 同样尊重显式 availability：放大包可获取，另两个保持即将推出。
        assertEquals(StoreAvailability.AVAILABLE, products.first { it.id == "hd-upscalers" }.availability)
        assertEquals(StoreAvailability.UPCOMING, products.first { it.id == "face-enhance" }.availability)
        assertEquals(StoreAvailability.UPCOMING, products.first { it.id == "memory-pack" }.availability)
    }

    @Test
    fun parserNormalizesAndDropsInvalidRows() {
        val json = """
            {
              "products": [
                {
                  "id": "  DUP ",
                  "name": "重复", "symbol": "x", "tagline": "t",
                  "description": "d", "category": "Tools",
                  "launchTarget": "", "kind": "APP"
                },
                {
                  "id": "dup",
                  "name": "重复小写", "symbol": "x", "tagline": "t",
                  "description": "d", "category": "Tools", "launchTarget": "dup"
                },
                {
                  "id": "broken",
                  "name": "",
                  "symbol": "x", "tagline": "t", "description": "d", "category": "Tools"
                },
                {
                  "id": "ok",
                  "name": "正常",
                  "symbol": "x", "tagline": "t", "description": "d",
                  "category": "Tools", "launchTarget": "ok_target",
                  "requiredDownloadBytes": -5
                }
              ]
            }
        """.trimIndent()
        val products = FancyStoreCatalogParser.parse(json)
        assertEquals(2, products.size)
        assertEquals("DUP", products[0].id)
        assertEquals("app", products[0].kind)
        assertEquals("DUP", products[0].launchTarget) // launchTarget 空白回退为 id
        assertEquals("ok_target", products[1].launchTarget)
        assertEquals(0L, products[1].requiredDownloadBytes) // 负值归零
    }

    @Test
    fun stateMachineCoversAllTransitions() {
        assertEquals(
            InstallStatus.INSTALLING,
            InstallStateMachine.transition(InstallStatus.NOT_INSTALLED, InstallAction.BEGIN),
        )
        assertEquals(
            InstallStatus.INSTALLED,
            InstallStateMachine.transition(InstallStatus.INSTALLING, InstallAction.COMPLETE),
        )
        assertEquals(
            InstallStatus.NOT_INSTALLED,
            InstallStateMachine.transition(InstallStatus.INSTALLED, InstallAction.UNINSTALL),
        )
        // 非法转移保持原状
        assertEquals(
            InstallStatus.INSTALLED,
            InstallStateMachine.transition(InstallStatus.INSTALLED, InstallAction.BEGIN),
        )
        assertEquals(
            InstallStatus.NOT_INSTALLED,
            InstallStateMachine.transition(InstallStatus.NOT_INSTALLED, InstallAction.COMPLETE),
        )
        assertEquals(
            InstallStatus.INSTALLING,
            InstallStateMachine.transition(InstallStatus.INSTALLING, InstallAction.UNINSTALL),
        )
    }

    @Test
    fun installStatusRoundTripsFromRaw() {
        assertEquals(InstallStatus.INSTALLED, InstallStatus.fromRaw("INSTALLED"))
        assertEquals(InstallStatus.INSTALLING, InstallStatus.fromRaw("INSTALLING"))
        assertEquals(InstallStatus.NOT_INSTALLED, InstallStatus.fromRaw("NOT_INSTALLED"))
        assertEquals(InstallStatus.NOT_INSTALLED, InstallStatus.fromRaw("garbage"))
        assertEquals(InstallStatus.NOT_INSTALLED, InstallStatus.fromRaw(null))
        assertFalse(InstallStatus.INSTALLED == InstallStatus.INSTALLING)
    }
}
