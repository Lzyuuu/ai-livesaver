package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldEventRoutingTest {
    @Test
    fun `non social event stays in current world destination`() {
        assertEquals("World", routeWorldEvent("identity_change", "World"))
        assertEquals("Me", routeWorldEvent("identity_change", "Me"))
    }

    @Test
    fun `social event routes to its space`() {
        assertEquals("Chats", routeWorldEvent("reconstructed_message", "World"))
        assertEquals("Commons", routeWorldEvent("npc_forum_reply", "World"))
        assertEquals("Moments", routeWorldEvent("character_interaction", "World"))
    }
}
