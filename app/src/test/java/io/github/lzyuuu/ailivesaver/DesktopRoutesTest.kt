package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopRoutesTest {
    @Test
    fun `dock exposes messenger imaging gallery store settings`() {
        assertEquals(
            listOf("Messenger", "Imaging", "Gallery", "商店", "Settings"),
            DesktopDockApps.map { it.label },
        )
        assertTrue(DesktopDockApps.contains(DesktopApp.Store))
    }

    @Test
    fun `composeDock stays fixed regardless of home installs`() {
        assertEquals(DesktopDockApps, DesktopNavigator.composeDock())
        assertEquals(5, DesktopNavigator.composeDock().size)
    }

    @Test
    fun `composeHomeGrid keeps characters first and orders on_home apps by home_order`() {
        val products = listOf(
            StoreProduct(
                id = "y",
                name = "Y",
                symbol = "Y",
                tagline = "t",
                description = "d",
                category = "Social",
                launchTarget = "y",
                kind = "app",
                builtIn = true,
                version = "1.0",
                features = emptyList(),
                requirements = emptyList(),
                requiredDownloadBytes = 0L,
                featured = false,
                availability = StoreAvailability.AVAILABLE,
            ),
            StoreProduct(
                id = "ustagram",
                name = "Ustagram",
                symbol = "◎",
                tagline = "t",
                description = "d",
                category = "Social",
                launchTarget = "ustagram",
                kind = "app",
                builtIn = true,
                version = "1.0",
                features = emptyList(),
                requirements = emptyList(),
                requiredDownloadBytes = 0L,
                featured = false,
                availability = StoreAvailability.AVAILABLE,
            ),
        )
        val installs = listOf(
            PersistedAppInstall("ustagram", InstallStatus.INSTALLED, 20L, true, 2, "1.0", 1L),
            PersistedAppInstall("y", InstallStatus.INSTALLED, 10L, true, 1, "1.0", 1L),
        )
        val grid = DesktopNavigator.composeHomeGrid(installs, products)
        assertEquals(listOf("characters", "y", "ustagram"), grid.map { it.app.route })
        assertEquals("Characters", grid[0].label)
        assertEquals(null, grid[0].symbol)
        assertEquals("Y", grid[1].label)
        assertEquals("Y", grid[1].symbol)
        assertEquals("Ustagram", grid[2].label)
        assertEquals("◎", grid[2].symbol)
        assertEquals("desktop-grid-characters", grid[0].testTag)
        assertEquals("desktop-grid-y", grid[1].testTag)
        assertEquals(null, grid[0].productId)
        assertFalse(grid[0].supportsLongPressMenu)
        assertEquals("y", grid[1].productId)
        assertTrue(grid[1].supportsLongPressMenu)
    }

    @Test
    fun `composeHomeGrid omits non home and unknown catalog apps`() {
        val products = listOf(
            StoreProduct(
                id = "rebbit",
                name = "Rebbit",
                symbol = "r/",
                tagline = "t",
                description = "d",
                category = "Social",
                launchTarget = "rebbit",
                kind = "app",
                builtIn = true,
                version = "1.0",
                features = emptyList(),
                requirements = emptyList(),
                requiredDownloadBytes = 0L,
                featured = false,
                availability = StoreAvailability.AVAILABLE,
            ),
        )
        val installs = listOf(
            PersistedAppInstall("rebbit", InstallStatus.INSTALLED, 1L, false, 0, "1.0", 1L),
            PersistedAppInstall("missing", InstallStatus.INSTALLED, 2L, true, 1, "1.0", 1L),
        )
        val grid = DesktopNavigator.composeHomeGrid(installs, products)
        assertEquals(1, grid.size)
        assertEquals(DesktopApp.Characters, grid[0].app)
    }

    @Test
    fun `store gating opens shell apps always and gates catalog apps by install state`() {
        // 壳层内置能力始终可达
        assertTrue(DesktopNavigator.isOpenable(DesktopApp.Store, installed = false))
        assertTrue(DesktopNavigator.isOpenable(DesktopApp.Messenger, installed = false))
        assertTrue(DesktopNavigator.isOpenable(DesktopApp.Characters, installed = false))
        // 目录 App：未安装不可达
        assertFalse(DesktopNavigator.isOpenable(DesktopApp.Y, installed = false))
        assertTrue(DesktopNavigator.isOpenable(DesktopApp.Y, installed = true))
        // 即将开放的 Games 按安装态门控（旧数据已装则可达）
        assertFalse(DesktopNavigator.isOpenable(DesktopApp.Games, installed = false))
        assertTrue(DesktopNavigator.isOpenable(DesktopApp.Games, installed = true))
    }

    @Test
    fun `social hub contains open social apps without paywall`() {
        val apps = DesktopHub.Social.apps
        assertTrue(apps.contains(DesktopApp.Ustagram))
        assertTrue(apps.contains(DesktopApp.Rebbit))
        assertTrue(apps.contains(DesktopApp.Y))
        assertTrue(apps.contains(DesktopApp.Phone))
        apps.forEach { app ->
            assertFalse(DesktopNavigator.isPaywalled(app))
            assertTrue(app.openEntry)
        }
    }

    @Test
    fun `entertainment hub lists games and games hub has six entries`() {
        assertEquals(listOf(DesktopApp.Games), DesktopHub.Entertainment.apps)
        assertEquals(6, GamesHubEntries.size)
        assertEquals(
            listOf(
                "world_adventure",
                "dice_duel_rpg",
                "tactical_command",
                "truth_or_dare",
                "two_truths_lie",
                "the_oracle",
            ),
            GamesHubEntries.map { it.id },
        )
        GamesHubEntries.forEach { entry ->
            assertTrue(entry.titleRes != 0)
            assertTrue(entry.descriptionRes != 0)
            assertTrue(entry.statusNoteRes != 0)
        }
        assertFalse(DesktopNavigator.isPaywalled(DesktopApp.Games))
    }

    @Test
    fun `composeFullAppGrid keeps home entries first and appends reachable shell apps`() {
        // 全新状态：无安装记录、空目录 -> 首页网格仅 Characters，
        // 补充项为常开壳层入口（按 DesktopApp 声明序，不含已在首页的 Characters）。
        val grid = DesktopNavigator.composeFullAppGrid(
            homeInstalls = emptyList(),
            products = emptyList(),
            installStatus = emptyMap(),
        )
        assertEquals(
            listOf("characters", "messenger", "imaging", "gallery", "settings", "store"),
            grid.map { it.app.route },
        )
        // 首页项保留 desktop-grid-* 标签与长按语义；补充项一律无 productId（无长按菜单）。
        assertEquals("desktop-grid-characters", grid[0].testTag)
        grid.drop(1).forEach { entry ->
            assertEquals(null, entry.productId)
            assertFalse(entry.supportsLongPressMenu)
        }
    }

    @Test
    fun `composeFullAppGrid includes installed catalog apps and gates uninstalled ones`() {
        val yProduct = StoreProduct(
            id = "y",
            name = "Y",
            symbol = "Y",
            tagline = "t",
            description = "d",
            category = "Social",
            launchTarget = "y",
            kind = "app",
            builtIn = true,
            version = "1.0",
            features = emptyList(),
            requirements = emptyList(),
            requiredDownloadBytes = 0L,
            featured = false,
            availability = StoreAvailability.AVAILABLE,
        )
        // 已安装但未上首页：Y 可达，作为补充项出现且不丢首页标签语义。
        val installed = DesktopNavigator.composeFullAppGrid(
            homeInstalls = emptyList(),
            products = listOf(yProduct),
            installStatus = mapOf("y" to InstallStatus.INSTALLED),
        )
        assertTrue(installed.any { it.app == DesktopApp.Y })
        assertEquals(null, installed.first { it.app == DesktopApp.Y }.productId)
        // 未安装：Y 不可达，不出现在全应用集合。
        val notInstalled = DesktopNavigator.composeFullAppGrid(
            homeInstalls = emptyList(),
            products = listOf(yProduct),
            installStatus = mapOf("y" to InstallStatus.NOT_INSTALLED),
        )
        assertFalse(notInstalled.any { it.app == DesktopApp.Y })
    }

    @Test
    fun `composeFullAppGrid does not duplicate on home apps and keeps their product ids`() {
        val yProduct = StoreProduct(
            id = "y",
            name = "Y",
            symbol = "Y",
            tagline = "t",
            description = "d",
            category = "Social",
            launchTarget = "y",
            kind = "app",
            builtIn = true,
            version = "1.0",
            features = emptyList(),
            requirements = emptyList(),
            requiredDownloadBytes = 0L,
            featured = false,
            availability = StoreAvailability.AVAILABLE,
        )
        val installs = listOf(
            PersistedAppInstall("y", InstallStatus.INSTALLED, 10L, true, 1, "1.0", 10L),
        )
        val grid = DesktopNavigator.composeFullAppGrid(
            homeInstalls = installs,
            products = listOf(yProduct),
            installStatus = mapOf("y" to InstallStatus.INSTALLED),
        )
        // on_home 的 Y 只出现一次（首页网格项），保留 productId 以支撑长按菜单。
        assertEquals(1, grid.count { it.app == DesktopApp.Y })
        val yEntry = grid.first { it.app == DesktopApp.Y }
        assertEquals("desktop-grid-y", yEntry.testTag)
        assertEquals("y", yEntry.productId)
        assertTrue(yEntry.supportsLongPressMenu)
        assertEquals(listOf("characters", "y"), grid.take(2).map { it.app.route })
    }

    @Test
    fun `hub and app navigation can return home`() {
        val hub = DesktopNavigator.openHub(DesktopHub.Social)
        assertEquals(DesktopRoute.Hub(DesktopHub.Social), hub)

        val app = DesktopNavigator.openApp(DesktopApp.Ustagram)
        assertEquals(DesktopRoute.App(DesktopApp.Ustagram), app)
        assertEquals(DesktopRoute.Home, DesktopNavigator.backFrom(app))
        assertEquals(DesktopRoute.Home, DesktopNavigator.backFrom(hub))

        val dockApp = DesktopNavigator.openApp(DesktopApp.Messenger)
        assertEquals(DesktopRoute.Home, DesktopNavigator.backFrom(dockApp))

        val gamesApp = DesktopNavigator.openApp(DesktopApp.Games)
        assertEquals(DesktopRoute.Home, DesktopNavigator.backFrom(gamesApp))
    }

    @Test
    fun `shell destinations exclude five tab labels`() {
        val shellLabels = DesktopHub.entries.map { it.label } +
            DesktopDockApps.map { it.label } +
            DesktopHub.Social.apps.map { it.label }
        listOf("World", "Chats", "Moments", "Commons", "Me").forEach { tab ->
            assertFalse(shellLabels.contains(tab))
        }
    }
}
