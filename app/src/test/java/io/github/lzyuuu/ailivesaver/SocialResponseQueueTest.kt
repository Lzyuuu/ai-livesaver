package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialResponseQueueTest {
    @Test
    fun queuesWhenResponseCannotRunAndNoJobWasRetried() {
        assertTrue(shouldQueueSocialResponse(true, false, null))
        assertFalse(shouldQueueSocialResponse(false, false, null))
        assertFalse(shouldQueueSocialResponse(true, true, null))
        assertFalse(shouldQueueSocialResponse(true, false, 7L))
    }

    @Test
    fun responseNeedsProviderBudgetAndUnpausedTasks() {
        assertTrue(canRespondToPost(providerReady = true, budgetAvailable = true, taskPaused = false))
        assertFalse(canRespondToPost(providerReady = false, budgetAvailable = true, taskPaused = false))
        assertFalse(canRespondToPost(providerReady = true, budgetAvailable = false, taskPaused = false))
        assertFalse(canRespondToPost(providerReady = true, budgetAvailable = true, taskPaused = true))
    }
}
