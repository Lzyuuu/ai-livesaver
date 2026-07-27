package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WorldNotificationTest {
    @Test
    fun `notification id is stable and separated per character`() {
        assertEquals(relationshipNotificationId(42), relationshipNotificationId(42))
        assertNotEquals(relationshipNotificationId(42), relationshipNotificationId(43))
    }
}
