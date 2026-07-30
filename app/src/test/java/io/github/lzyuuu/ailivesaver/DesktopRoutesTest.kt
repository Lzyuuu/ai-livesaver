package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopRoutesTest {
    @Test
    fun `dock exposes messenger imaging gallery settings`() {
        assertEquals(
            listOf("Messenger", "Imaging", "Gallery", "Settings"),
            DesktopDockApps.map { it.label },
        )
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
        assertFalse(DesktopNavigator.isPaywalled(DesktopApp.Games))
    }

    @Test
    fun `hub and app navigation can return home`() {
        val hub = DesktopNavigator.openHub(DesktopHub.Social)
        assertEquals(DesktopRoute.Hub(DesktopHub.Social), hub)

        val app = DesktopNavigator.openApp(DesktopApp.Ustagram)
        assertEquals(DesktopRoute.App(DesktopApp.Ustagram), app)
        assertEquals(DesktopRoute.Hub(DesktopHub.Social), DesktopNavigator.backFrom(app))
        assertEquals(DesktopRoute.Home, DesktopNavigator.backFrom(hub))

        val dockApp = DesktopNavigator.openApp(DesktopApp.Messenger)
        assertEquals(DesktopRoute.Home, DesktopNavigator.backFrom(dockApp))
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
