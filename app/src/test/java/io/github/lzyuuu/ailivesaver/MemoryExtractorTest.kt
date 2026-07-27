package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryExtractorTest {
    @Test
    fun keepsLikelyPersonalFactsOnly() {
        assertEquals("请记住我喜欢雨天。", MemoryExtractor.fromUserMessage("请记住我喜欢雨天。"))
        assertEquals("Remember that I like rain.", MemoryExtractor.fromUserMessage("Remember that I like rain."))
        assertNull(MemoryExtractor.fromUserMessage("你好"))
    }
}
