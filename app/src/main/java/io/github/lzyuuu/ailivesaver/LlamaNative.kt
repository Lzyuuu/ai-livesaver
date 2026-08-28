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

    /** 加载模型，返回会话句柄；失败返回 0。 */
    external fun nativeLoadModel(path: String, nCtx: Int, nThreads: Int): Long

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
    external fun nativeBenchmark(path: String, steps: Int, nThreads: Int): Float

    fun interface TokenCallback {
        fun onToken(token: String)
    }
}
