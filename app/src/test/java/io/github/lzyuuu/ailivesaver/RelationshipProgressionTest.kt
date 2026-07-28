package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelationshipProgressionTest {
    @Test
    fun `conversation milestones stay narrative and bounded`() {
        val new = RelationshipState("刚认识", "", null, 0)
        val started = nextRelationship(new, sharedPersonalFact = true)
        assertEquals("开始交谈", started?.label)
        assertEquals(1, started?.closeness)
        assertEquals(1, started?.trust)
        assertEquals(0, started?.tension)

        val talking = RelationshipState(
            "开始交谈",
            "",
            1,
            1,
            closeness = 1,
            trust = 1,
        )
        val known = nextRelationship(talking, sharedPersonalFact = true)
        assertEquals("更了解彼此", known?.label)
        assertEquals(2, known?.closeness)
        assertEquals(3, known?.trust)

        val closer = RelationshipState("更了解彼此", "", 2, 2)
        assertNull(nextRelationship(closer, sharedPersonalFact = true))

        val pinned = RelationshipState("刚认识", "", null, 0, pinned = true)
        assertNull(nextRelationship(pinned, sharedPersonalFact = true))

        val tension = RelationshipState(
            "更了解彼此",
            "",
            2,
            2,
            closeness = 3,
            trust = 4,
        )
        val cracked = nextRelationship(
            tension,
            sharedPersonalFact = false,
            signals = RelationshipSignals(tension = true, repair = false),
        )
        assertEquals(
            "关系出现裂痕",
            cracked?.label,
        )
        assertEquals(2, cracked?.closeness)
        assertEquals(3, cracked?.trust)
        assertEquals(3, cracked?.tension)
        val distant = RelationshipState(
            "关系出现裂痕",
            "",
            3,
            3,
            closeness = 2,
            trust = 3,
            tension = 3,
        )
        assertEquals(
            "有些疏远",
            nextRelationship(
                distant,
                sharedPersonalFact = false,
                signals = RelationshipSignals(tension = true, repair = false),
            )?.label,
        )
        val repaired = nextRelationship(
            distant,
            sharedPersonalFact = false,
            signals = RelationshipSignals(tension = false, repair = true),
        )
        assertEquals("重新靠近", repaired?.label)
        assertEquals(3, repaired?.closeness)
        assertEquals(5, repaired?.trust)
        assertEquals(0, repaired?.tension)
    }

    @Test
    fun `message cues stay narrow and directional`() {
        assertEquals(true, relationshipSignals("我很失望，不想聊").tension)
        assertEquals(true, relationshipSignals("对不起，我们谈谈").repair)
        assertEquals(false, relationshipSignals("今天见到一只猫").tension)
    }
}
