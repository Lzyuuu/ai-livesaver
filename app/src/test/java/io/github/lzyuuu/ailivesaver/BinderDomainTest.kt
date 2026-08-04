package io.github.lzyuuu.ailivesaver

import org.junit.Assert.*
import org.junit.Test

class BinderDomainTest {
    @Test fun answers_roundTrip() {
        val value = BinderAnswers(relationship="朋友", interests="音乐")
        assertEquals(value, BinderAnswers.fromJson(value.toJson()))
    }
    @Test fun candidate_requiresTypedShape() {
        val raw = "{\"candidates\":[{\"name\":\"Mira\",\"persona\":\"温柔\",\"relationship\":\"朋友\",\"reasons\":[\"音乐\"]}]}"
        assertEquals("Mira", validateBinderCandidateList(raw).single().name)
    }
    @Test fun malformedCandidateRejected() {
        assertThrows(IllegalArgumentException::class.java) { validateBinderCandidateList("{\"candidates\":[{\"name\":\"x\"}]}") }
        assertThrows(Exception::class.java) { validateBinderCandidateList("not json") }
    }
    @Test fun candidateCountBounded() {
        assertThrows(IllegalArgumentException::class.java) { validateBinderCandidateList("{\"candidates\":[]}") }
    }
}
