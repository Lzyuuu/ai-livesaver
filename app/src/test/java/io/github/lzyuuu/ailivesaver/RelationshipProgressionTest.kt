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

        val tension = RelationshipState("更了解彼此", "", 2, 2)
        assertEquals(
            "关系出现裂痕",
            nextRelationship(
                tension,
                sharedPersonalFact = false,
                signals = RelationshipSignals(tension = true, repair = false),
            )?.first,
        )
        val distant = RelationshipState("关系出现裂痕", "", 3, 3)
        assertEquals(
            "有些疏远",
            nextRelationship(
                distant,
                sharedPersonalFact = false,
                signals = RelationshipSignals(tension = true, repair = false),
            )?.first,
        )
        assertEquals(
            "重新靠近",
            nextRelationship(
                distant,
                sharedPersonalFact = false,
                signals = RelationshipSignals(tension = false, repair = true),
            )?.first,
        )
    }

    @Test
    fun `message cues stay narrow and directional`() {
        assertEquals(true, relationshipSignals("我很失望，不想聊").tension)
        assertEquals(true, relationshipSignals("对不起，我们谈谈").repair)
        assertEquals(false, relationshipSignals("今天见到一只猫").tension)
    }
}
