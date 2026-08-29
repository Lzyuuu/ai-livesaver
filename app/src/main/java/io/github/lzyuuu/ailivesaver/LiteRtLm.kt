package io.github.lzyuuu.ailivesaver

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.SessionConfig

/** Minimal adapter around the official LiteRT-LM Android runtime. */
internal object LiteRtLm {
    val isAvailable: Boolean = runCatching {
        System.loadLibrary("litertlm_jni")
        true
    }.getOrDefault(false)

    val supportedBackends: Set<InferenceBackend>
        get() = if (isAvailable) setOf(InferenceBackend.CPU, InferenceBackend.GPU, InferenceBackend.NPU) else emptySet()

    private var cachedEngine: Engine? = null
    private var cachedPath: String? = null
    private var cachedBackend: InferenceBackend? = null

    /** 按模型路径+后端缓存 Engine；模型加载成本高，切忌每条消息重复 initialize。 */
    @Synchronized
    fun engine(context: Context, modelPath: String, backend: InferenceBackend): Engine {
        check(isAvailable) { "LiteRT-LM runtime is unavailable" }
        val existing = cachedEngine
        if (existing != null && cachedPath == modelPath && cachedBackend == backend) {
            check(existing.isInitialized()) { "LiteRT-LM engine lost its state" }
            return existing
        }
        existing?.close()
        val nativeBackend = when (backend) {
            InferenceBackend.CPU -> Backend.CPU
            InferenceBackend.GPU -> Backend.GPU
            InferenceBackend.NPU -> Backend.NPU
        }
        // maxNumTokens 对齐聊天上下文字典配置；cacheDir 由 native 用于 KV 缓存落盘。
        val created = Engine(
            EngineConfig(
                modelPath,
                nativeBackend,
                null,
                null,
                2048,
                context.cacheDir.absolutePath,
            ),
        ).also { it.initialize() }
        cachedEngine = created
        cachedPath = modelPath
        cachedBackend = backend
        return created
    }

    @Synchronized
    fun release() {
        cachedEngine?.close()
        cachedEngine = null
        cachedPath = null
        cachedBackend = null
    }

    fun samplerConfig(generation: GenerationSettings): SessionConfig {
        val normalized = generation.normalized()
        return SessionConfig(
            SamplerConfig(
                topK = 256,
                topP = normalized.topP.toDouble(),
                temperature = normalized.temperature.toDouble(),
                seed = 0,
            ),
        )
    }

    private fun defaultSamplerConfig(): SessionConfig =
        samplerConfig(GenerationSettings())

    /**
     * Benchmark（SO-10 LiteRT 分支）：CPU 后端逐次生成，返回平均墙钟秒/次。
     * 公开 alpha05 无 token 计数 API，故报告 s/次而非伪造 tokens/s；
     * 失败抛出，由调用方显示"基准失败"。
     */
    fun benchmark(context: Context, modelPath: String, runs: Int): Double {
        check(isAvailable) { "LiteRT-LM runtime is unavailable" }
        require(runs > 0) { "runs must be positive" }
        val config = EngineConfig(
            modelPath,
            Backend.CPU,
            null,
            null,
            512,
            context.cacheDir.absolutePath,
        )
        val engine = Engine(config).also { it.initialize() }
        try {
            var totalSeconds = 0.0
            repeat(runs) {
                val startedAt = android.os.SystemClock.elapsedRealtime()
                engine.createSession(defaultSamplerConfig()).use { session ->
                    session.generateContent(listOf(InputData.Text("你好，请用一句话介绍你自己。")))
                }
                totalSeconds += (android.os.SystemClock.elapsedRealtime() - startedAt) / 1000.0
            }
            return totalSeconds / runs
        } finally {
            engine.close()
        }
    }

    fun generateOnce(
        engine: Engine,
        sessionConfig: SessionConfig,
        prompt: String,
    ): String =
        engine.createSession(sessionConfig).use { session ->
            session.generateContent(listOf(InputData.Text(prompt)))
        }
}
