package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagingStudioTest {
    @Test
    fun parsesBackendRoutes() {
        assertEquals(ImagingBackend.OnDevice, ImagingBackend.fromRoute(null))
        assertEquals(ImagingBackend.OnDevice, ImagingBackend.fromRoute("on_device"))
        assertEquals(ImagingBackend.Forge, ImagingBackend.fromRoute("forge"))
        assertEquals(ImagingBackend.LocalDream, ImagingBackend.fromRoute("local_dream"))
        assertEquals(ImagingBackend.OnDevice, ImagingBackend.fromRoute("unknown"))
    }

    @Test
    fun normalizesForgeBaseUrl() {
        assertEquals("http://127.0.0.1:7860", normalizeForgeBaseUrl(""))
        assertEquals("http://10.0.2.2:7860", normalizeForgeBaseUrl("http://10.0.2.2:7860/"))
        assertEquals("http://192.168.1.8:7860", normalizeForgeBaseUrl("  http://192.168.1.8:7860  "))
    }

    @Test
    fun defaultSettingsMatchReferenceStudio() {
        val settings = ImagingStudioSettings()
        assertEquals(ImagingBackend.OnDevice, settings.backend)
        assertEquals(20, settings.steps)
        assertEquals(7.0, settings.cfg, 0.0001)
        assertEquals(0L, settings.seed)
        assertEquals(false, settings.seedLocked)
        assertEquals(512, settings.width)
        assertEquals(512, settings.height)
        assertTrue(settings.forgeUrl.startsWith("http://"))
    }
}
