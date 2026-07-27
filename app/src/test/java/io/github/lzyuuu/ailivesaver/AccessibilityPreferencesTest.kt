package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityPreferencesTest {
    @Test
    fun systemAnimationScalesDisableMotionWhenAnyScaleIsZero() {
        assertTrue(animationsEnabled(1f, 1f, 1f))
        assertFalse(animationsEnabled(0f, 1f, 1f))
        assertFalse(animationsEnabled(1f, 0f, 1f))
        assertFalse(animationsEnabled(1f, 1f, 0f))
    }
}
