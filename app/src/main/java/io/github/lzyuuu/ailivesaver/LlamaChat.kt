package io.github.lzyuuu.ailivesaver

import android.content.Context
import java.io.File

/**
 * 本地 Gemma 聊天引擎（阶段② #73 开工项 4）：
 * 会话句柄常驻缓存（加载一次 8.3s，避免每条消息重载），
 * prompt 用 Gemma it 模板 + 指令模板 + 最近对话，专家采样参数编码进 sampler chain。
 */
internal object LlamaChat {
    private const val MAX_TURNS_TOKEN_BUDGET = 2048

    private var handle = 0L
    private var loadedPath: String? = null

    @Synchronized
    private fun ensureLoaded(modelPath: String): Long {
        if (handle != 0L && loadedPath == modelPath) return handle
        if (handle != 0L) LlamaNative.nativeFree(handle)
        handle = LlamaNative.nativeLoadModel(modelPath, MAX_TURNS_TOKEN_BUDGET, 4)
        loadedPath = if (handle != 0L) modelPath else null
        return handle
    }

    @Synchronized
    fun release() {
        if (handle != 0L) LlamaNative.nativeFree(handle)
        handle = 0L
        loadedPath = null
    }

    /** 专家采样 → JNI sampling 数组（顺序见 llama_jni.cpp）。 */
    internal fun encodeSampling(generation: GenerationSettings): FloatArray {
        val s = generation.normalized().expertSampling
        return floatArrayOf(
            generation.normalized().temperature,
            generation.normalized().topP,
            s.minP,
            s.repetitionPenalty,
            s.penaltyWindow.toFloat(),
            s.xtcSurprise,
            s.xtcFloor,
            s.dryLoopBreaker,
            s.drySteepness,
            s.dryAllowedRepeat.toFloat(),
        )
    }

    internal fun buildPrompt(
        systemInstruction: String,
        recent: List<Pair<String, String>>,
        latestUser: String,
    ): String {
        // Gemma it 模板：system 融入首 turn，历史按 user/model 交替。
        val builder = StringBuilder()
        builder.append("<start_of_turn>user\n")
        if (systemInstruction.isNotBlank()) {
            builder.append(systemInstruction.trim()).append("\n\n")
        }
        recent.forEach { (sender, body) ->
            builder.append(if (sender == "user") "用户: " else "角色: ")
                .append(body.take(400))
                .append("\n")
        }
        builder.append(latestUser)
        builder.append("<end_of_turn>\n<start_of_turn>model\n")
        return builder.toString()
    }

    /**
     * 本地流式补全。onDelta/callback 与 ProviderChatClient.stream 的语义对齐，
     * 失败时 Result.failure，由调用方回退云端或报错。
     */
    fun stream(
        context: Context,
        characterName: String,
        persona: String,
        systemInstruction: String,
        recent: List<Pair<String, String>>,
        latestUser: String,
        generation: GenerationSettings,
        onDelta: (String) -> Unit,
        callback: (Result<String>) -> Unit,
    ) {
        val entry = LocalModels.load(context).firstOrNull {
            it.id == LocalModels.activeEngineId(context)
        }
        if (entry == null) {
            callback(Result.failure(IllegalStateException("本地引擎未配置")))
            return
        }
        val modelPath = File(HfModelStore.directory(context), "${entry.id}/${entry.fileName}").absolutePath
        Thread {
            val result = runCatching {
                val session = ensureLoaded(modelPath)
                if (session == 0L) error("本地模型加载失败")
                val prompt = buildPrompt(systemInstruction, recent, latestUser)
                val full = StringBuilder()
                val sampled = LlamaNative.nativeStreamCompletion(
                    session,
                    prompt,
                    256,
                    encodeSampling(generation),
                ) { token ->
                    full.append(token)
                    onDelta(full.toString())
                }
                if (sampled <= 0) error("本地补全失败")
                full.toString().trim()
            }
            callback(result)
        }.start()
    }
}
