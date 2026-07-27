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

    @Test
    fun candidate_hints_allow_provider_backed_capture() {
        assertEquals(true, MemoryExtractor.shouldInspect("我最近开始在上海工作。"))
        assertEquals(false, MemoryExtractor.shouldInspect("今天天气不错。"))
        assertEquals("我最近开始在上海工作。", MemoryExtractor.fromProvider("我最近开始在上海工作。"))
        assertNull(MemoryExtractor.fromProvider("NONE"))
    }
}
