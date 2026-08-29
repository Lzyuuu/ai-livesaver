package io.github.lzyuuu.ailivesaver

import android.content.Context
import org.json.JSONObject

/**
 * 引擎内部参数（对齐参考 ref-30c「上下文 / 引擎内部」）：
 * 对话长度、预填充/解码线程（0 = 自动）、批大小。持久化于 engine_internals 偏好。
 * 更改在下次加载模型时生效（llama.cpp 上下文重建）。
 */
internal data class EngineInternals(
    val contextTokens: Int = DEFAULT.contextTokens,
    val prefillThreads: Int = 0,
    val decodeThreads: Int = 0,
    val batchSize: Int = DEFAULT.batchSize,
) {
    fun normalized(): EngineInternals = copy(
        contextTokens = contextTokens.coerceIn(MIN_CONTEXT_TOKENS, MAX_CONTEXT_TOKENS),
        prefillThreads = prefillThreads.coerceIn(0, MAX_THREADS),
        decodeThreads = decodeThreads.coerceIn(0, MAX_THREADS),
        batchSize = batchSize.coerceIn(MIN_BATCH, MAX_BATCH),
    )

    companion object {
        val DEFAULT = EngineInternals(contextTokens = 2048, batchSize = 512)
        const val MIN_CONTEXT_TOKENS = 512
        const val MAX_CONTEXT_TOKENS = 16384
        const val MIN_BATCH = 64
        const val MAX_BATCH = 2048
        const val MAX_THREADS = 16
        const val CONTEXT_STEP = 512
        const val BATCH_STEP = 64
    }
}

internal object EngineInternalsStore {
    private const val PREFS = "engine_internals"
    private const val KEY_JSON = "internals"

    fun read(context: Context): EngineInternals =
        parse(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_JSON, null),
        )

    fun write(context: Context, internals: EngineInternals) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_JSON, encode(internals.normalized()))
            .apply()
    }

    internal fun parse(raw: String?): EngineInternals {
        if (raw.isNullOrBlank()) return EngineInternals.DEFAULT
        return runCatching {
            val o = JSONObject(raw)
            EngineInternals(
                contextTokens = o.optInt("context_tokens", EngineInternals.DEFAULT.contextTokens),
                prefillThreads = o.optInt("prefill_threads", 0),
                decodeThreads = o.optInt("decode_threads", 0),
                batchSize = o.optInt("batch_size", EngineInternals.DEFAULT.batchSize),
            ).normalized()
        }.getOrDefault(EngineInternals.DEFAULT)
    }

    internal fun encode(internals: EngineInternals): String =
        JSONObject()
            .put("context_tokens", internals.contextTokens)
            .put("prefill_threads", internals.prefillThreads)
            .put("decode_threads", internals.decodeThreads)
            .put("batch_size", internals.batchSize)
            .toString()
}

/** 线程数解析：0（自动）按设备核数推导；显式值夹在 1..cores。 */
internal fun resolveThreads(setting: Int, autoThreads: Int, cores: Int): Int =
    if (setting <= 0) autoThreads.coerceIn(1, cores) else setting.coerceIn(1, cores)

/** 自动预填充：参考表现为「多核短时爆发」——约一半核数向上取整。 */
internal fun prefillAutoThreads(cores: Int): Int = ((cores + 1) / 2).coerceIn(1, cores)

/** 自动解码：参考表现为「低核长时稳定」——约四分之一核数，至少 1。 */
internal fun decodeAutoThreads(cores: Int): Int = (cores / 4).coerceIn(1, cores)
