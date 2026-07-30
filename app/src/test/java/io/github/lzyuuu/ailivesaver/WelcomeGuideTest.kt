package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeGuideTest {
    @Test
    fun `notification then root dialogue reaches name step`() {
        var state = WelcomeGuideState()
        state = WelcomeGuide.afterNotificationHandled(state)
        assertEquals(WelcomeStep.RootIntro1, state.step)
        state = WelcomeGuide.continueFrom(state)
        assertEquals(WelcomeStep.RootIntro2, state.step)
        state = WelcomeGuide.continueFrom(state)
        assertEquals(WelcomeStep.UserName, state.step)
    }

    @Test
    fun `name and appearance six-choice then optional about`() {
        var state = WelcomeGuideState(step = WelcomeStep.UserName)
        assertFalse(WelcomeGuide.continueFrom(state).step == WelcomeStep.Appearance)

        state = WelcomeGuide.withName(state, "焰宇")
        state = WelcomeGuide.continueFrom(state)
        assertEquals(WelcomeStep.Appearance, state.step)

        assertEquals(6, RootAppearance.entries.size)
        state = WelcomeGuide.withAppearance(state, RootAppearance.AnimeBlonde)
        state = WelcomeGuide.continueFrom(state)
        assertEquals(WelcomeStep.About, state.step)

        state = WelcomeGuide.withAbout(state, "喜欢深夜电台")
        state = WelcomeGuide.continueFrom(state)
        assertEquals(WelcomeStep.SetupMode, state.step)
    }

    @Test
    fun `easy local failure can walk in to finished`() {
        var state = WelcomeGuideState(step = WelcomeStep.SetupMode)
        state = WelcomeGuide.withSetupMode(state, WelcomeSetupMode.Easy)
        state = WelcomeGuide.continueFrom(state)
        assertEquals(WelcomeStep.AiMode, state.step)

        state = WelcomeGuide.withAiMode(state, WelcomeAiMode.Local)
        state = WelcomeGuide.continueFrom(state)
        assertEquals(WelcomeStep.LocalDownload, state.step)

        state = WelcomeGuide.markLocalFailed(state)
        assertTrue(state.localFailed)
        state = WelcomeGuide.walkIn(state)
        assertTrue(WelcomeGuide.isFinished(state))
        assertEquals(WelcomeStep.Finished, state.step)
    }

    @Test
    fun `cloud path finishes after provider chosen`() {
        var state = WelcomeGuideState(step = WelcomeStep.AiMode)
        state = WelcomeGuide.withAiMode(state, WelcomeAiMode.Cloud)
        state = WelcomeGuide.continueFrom(state)
        assertEquals(WelcomeStep.CloudSetup, state.step)

        assertFalse(WelcomeGuide.continueFrom(state).step == WelcomeStep.Finished)
        state = WelcomeGuide.withCloudProvider(state, "OpenRouter")
        state = WelcomeGuide.continueFrom(state)
        assertTrue(WelcomeGuide.isFinished(state))
    }

    @Test
    fun `back from local download returns to ai mode`() {
        val state = WelcomeGuide.backFrom(
            WelcomeGuideState(step = WelcomeStep.LocalDownload, localFailed = true),
        )
        assertEquals(WelcomeStep.AiMode, state.step)
        assertFalse(state.localFailed)
    }
}
