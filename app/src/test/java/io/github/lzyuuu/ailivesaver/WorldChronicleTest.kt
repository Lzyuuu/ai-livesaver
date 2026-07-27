package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldChronicleTest {
    @Test
    fun markAllReadUsesTheCompleteChronicle() {
        val events = listOf(
            WorldEvent(1, "moment", "old", "A", true, false, 1, null, "", ""),
            WorldEvent(2, "message", "new", "B", true, true, 2, null, "", ""),
            WorldEvent(3, "forum", "older", "C", true, false, 3, null, "", ""),
        )
        assertEquals(listOf(1L, 3L), unreadWorldEventIds(events))
    }

    @Test
    fun recentQueriesStayBoundedWhileChronicleCanReadAll() {
        assertEquals("LIMIT 20", worldEventLimitClause(20))
        assertEquals("LIMIT 1", worldEventLimitClause(0))
        assertEquals("", worldEventLimitClause(null))
    }
}
