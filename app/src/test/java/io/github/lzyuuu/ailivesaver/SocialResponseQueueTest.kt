package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialResponseQueueTest {
    @Test
    fun queuesOnlyWhenACharacterExistsAndNoJobWasRetried() {
        assertTrue(shouldQueueSocialResponse(true, false, null))
        assertFalse(shouldQueueSocialResponse(false, false, null))
        assertFalse(shouldQueueSocialResponse(true, true, null))
        assertFalse(shouldQueueSocialResponse(true, false, 7L))
    }
}
