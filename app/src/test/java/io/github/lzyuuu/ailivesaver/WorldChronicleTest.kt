package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldChronicleTest {
    @Test
    fun recentQueriesStayBoundedWhileChronicleCanReadAll() {
        assertEquals("LIMIT 20", worldEventLimitClause(20))
        assertEquals("LIMIT 1", worldEventLimitClause(0))
        assertEquals("", worldEventLimitClause(null))
    }
}
