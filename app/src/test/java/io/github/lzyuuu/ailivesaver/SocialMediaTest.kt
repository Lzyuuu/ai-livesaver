package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialMediaTest {
    @Test
    fun renderedImagesUsePowerOfTwoSampling() {
        assertEquals(1, renderedImageSampleSize(1024, 2048))
        assertEquals(2, renderedImageSampleSize(4096, 2048))
        assertEquals(4, renderedImageSampleSize(8192, 4096))
        assertEquals(1, renderedImageSampleSize(0, 4096))
    }

    @Test
    fun socialComposerRequiresAWorldAndAnExplicitOpenAction() {
        assertFalse(socialComposerVisible(hasCharacter = false, expanded = true))
        assertFalse(socialComposerVisible(hasCharacter = true, expanded = false))
        assertTrue(socialComposerVisible(hasCharacter = true, expanded = true))
    }
}
