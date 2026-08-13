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
    fun `composeDock appends home apps without displacing store`() {
        val dock = DesktopNavigator.composeDock(listOf(DesktopApp.Y, DesktopApp.Ustagram, DesktopApp.Settings))
        assertEquals(DesktopApp.Store, dock[3])
        assertTrue(dock.contains(DesktopApp.Y))
        assertTrue(dock.contains(DesktopApp.Ustagram))
        assertEquals(1, dock.count { it == DesktopApp.Settings })
        assertEquals(listOf("Messenger", "Imaging", "Gallery", "商店", "Settings", "Y", "Ustagram"), dock.map { it.label })
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
