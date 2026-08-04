package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNavigationTest {
    @Test fun resolvesTypedCompatibilityKeys() {
        assertEquals(SettingsDestination.LOCAL_DREAM, resolveSettingsDestination("local-dream"))
        assertEquals(SettingsDestination.IDENTITY, resolveSettingsDestination("identity"))
    }

    @Test fun searchesChineseSynonymsWithStableOrdering() {
        assertEquals(SettingsDestination.STORAGE, searchSettings("存储").single().destination)
        assertTrue(searchSettings("about").any { it.destination == SettingsDestination.ABOUT })
        assertEquals(searchSettings("about"), searchSettings("about"))
    }
}
