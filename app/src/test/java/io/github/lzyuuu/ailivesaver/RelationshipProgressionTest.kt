package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelationshipProgressionTest {
    @Test
    fun `conversation milestones stay narrative and bounded`() {
        val new = RelationshipState("刚认识", "", null, 0)
        val started = nextRelationship(new, sharedPersonalFact = true)
        assertEquals("开始交谈", started?.first)

        val talking = RelationshipState("开始交谈", "", 1, 1)
        assertEquals("更了解彼此", nextRelationship(talking, sharedPersonalFact = true)?.first)

        val closer = RelationshipState("更了解彼此", "", 2, 2)
        assertNull(nextRelationship(closer, sharedPersonalFact = true))

        val pinned = RelationshipState("刚认识", "", null, 0, pinned = true)
        assertNull(nextRelationship(pinned, sharedPersonalFact = true))
    }
}
