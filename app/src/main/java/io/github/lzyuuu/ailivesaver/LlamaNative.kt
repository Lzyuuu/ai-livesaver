package io.github.lzyuuu.ailivesaver

/**
 * llama.cpp 最小 JNI 桥（阶段② #73 开工项 2）。
 * so 仅在 LLAMA_CPP_DIR 源码存在时编入（见 cpp/CMakeLists.txt），
 * 加载失败（设备无 so）时 [isAvailable] 为 false，云端路线不受影响。
 */
internal object LlamaNative {
    var isAvailable: Boolean = false
        private set

    init {
        isAvailable = runCatching { System.loadLibrary("llama_jni") }.isSuccess
    }

    /**
     * 运行时能力检测：当前编入的 llama_jni 实际支持的推理后端。
     * 由 [supportedInferenceBackends] 声明（纯 CPU，见 InferenceBackend.kt 注释），
     * 与 JNI 构建（n_gpu_layers=0、仅 ggml-cpu 变体）保持一致；
     * 库缺失时为空集合，表示无任何本地后端。
     */
    val supportedBackends: Set<InferenceBackend>
        get() = supportedInferenceBackends(isAvailable)

    /** 加载模型，返回会话句柄；失败返回 0。线程/批大小来自引擎内部参数（EngineInternals）。 */
    external fun nativeLoadModel(
        path: String,
        nCtx: Int,
        nDecodeThreads: Int,
        nPrefillThreads: Int,
        nBatch: Int,
    ): Long

    /**
     * 流式补全：逐 token 回调 [TokenCallback.onToken]，返回完整文本长度。
     * [sampling] 编码专家采样参数（见 cpp/llama_jni.cpp）：10 floats =
     * temperature, topP, minP, repetitionPenalty, penaltyWindow,
     * xtcProbability, xtcThreshold, dryMultiplier, dryBase, dryAllowedLength。
     * 传 null 走贪心。
     */
    external fun nativeStreamCompletion(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        sampling: FloatArray?,
        callback: TokenCallback,
    ): Int

    external fun nativeFree(handle: Long)

    /** Benchmark（SO-10）：加载→热身→计时 [steps] token，返回 tok/s；失败 -1。 */
    external fun nativeBenchmark(
        path: String,
        steps: Int,
        nDecodeThreads: Int,
        nPrefillThreads: Int,
        nBatch: Int,
    ): Float

    fun interface TokenCallback {
        fun onToken(token: String)
    }
}
