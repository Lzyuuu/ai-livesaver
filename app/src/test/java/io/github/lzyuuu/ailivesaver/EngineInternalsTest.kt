package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 引擎内部参数：归一化、JSON 往返、自动线程解析。 */
class EngineInternalsTest {
    @Test
    fun defaultsMatchReferenceValues() {
        val internals = EngineInternals.DEFAULT
        assertEquals(2048, internals.contextTokens)
        assertEquals(512, internals.batchSize)
        assertEquals(0, internals.prefillThreads) // 0 = 自动
        assertEquals(0, internals.decodeThreads)
    }

    @Test
    fun normalizedClampsOutOfRangeValues() {
        val normalized = EngineInternals(
            contextTokens = 1,
            prefillThreads = 99,
            decodeThreads = -5,
            batchSize = 999999,
        ).normalized()
        assertEquals(EngineInternals.MIN_CONTEXT_TOKENS, normalized.contextTokens)
        assertEquals(EngineInternals.MAX_THREADS, normalized.prefillThreads)
        assertEquals(0, normalized.decodeThreads)
        assertEquals(EngineInternals.MAX_BATCH, normalized.batchSize)
    }

    @Test
    fun encodeParseRoundTrips() {
        val internals = EngineInternals(
            contextTokens = 4096,
            prefillThreads = 3,
            decodeThreads = 2,
            batchSize = 256,
        ).normalized()
        assertEquals(internals, EngineInternalsStore.parse(EngineInternalsStore.encode(internals)))
    }

    @Test
    fun parseMalformedOrMissingFallsBackToDefaults() {
        assertEquals(EngineInternals.DEFAULT, EngineInternalsStore.parse(null))
        assertEquals(EngineInternals.DEFAULT, EngineInternalsStore.parse(""))
        assertEquals(EngineInternals.DEFAULT, EngineInternalsStore.parse("not json"))
        // 合法 JSON 但字段缺失时，缺失字段回默认值。
        val partial = EngineInternalsStore.parse("{\"context_tokens\": 1024}")
        assertEquals(1024, partial.contextTokens)
        assertEquals(EngineInternals.DEFAULT.batchSize, partial.batchSize)
    }

    @Test
    fun autoThreadResolutionFollowsReferencePolicy() {
        // 4 核：预填充自动 2、解码自动 1（与参考截图「自动 — 2 核 / 自动 — 1 核」一致）。
        assertEquals(2, prefillAutoThreads(4))
        assertEquals(1, decodeAutoThreads(4))
        // 8 核：预填充 4、解码 2；至少 1。
        assertEquals(4, prefillAutoThreads(8))
        assertEquals(2, decodeAutoThreads(8))
        assertEquals(1, decodeAutoThreads(2))
        // 自动值按策略解析后夹在 1..cores。
        assertEquals(2, resolveThreads(0, prefillAutoThreads(4), 4))
        // 显式值夹在 1..cores：9 核请求在 4 核设备上得 4；合法显式值原样通过。
        assertEquals(4, resolveThreads(9, prefillAutoThreads(4), 4))
        assertEquals(2, resolveThreads(2, prefillAutoThreads(4), 4))
    }

    @Test
    fun benchmarkFormattingIsHonestPerEngineType() {
        // llama.cpp：真实 tokens/s 计数。
        assertEquals("5.50 tokens/s（3 次平均）", formatBenchmarkScores(listOf(5f, 6f, 5.5f)))
        // LiteRT：公开运行时无 token 计数 API，报告墙钟 s/次，不伪造 tokens/s。
        assertEquals("0.50 s/次（2 次平均 · LiteRT 墙钟）", formatLiteRtBenchmark(0.5, 2))
        assertTrue(formatBenchmarkScores(listOf(1f)).contains("tokens/s"))
        assertFalse(formatLiteRtBenchmark(0.5, 1).contains("tokens/s"))
        assertEquals("基准失败（请检查模型与内存）", BENCHMARK_FAIL_LINE)
    }
}
