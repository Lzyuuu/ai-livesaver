package io.github.lzyuuu.ailivesaver

import android.content.Context

enum class InferenceBackend(val wireName: String, val label: String) {
    CPU("cpu", "CPU"),
    GPU("gpu", "GPU"),
    NPU("npu", "NPU");

    companion object {
        fun fromWireName(value: String?): InferenceBackend =
            entries.firstOrNull { it.wireName == value } ?: CPU
    }
}

/**
 * 能力检测（纯函数，可 JVM 单测）：llama_jni 运行时实际可用的推理后端集合。
 *
 * 当前编入的 llama_jni 为纯 CPU 运行时——JNI 侧固定 n_gpu_layers=0（见
 * llama_jni.cpp），jniLibs 仅捆绑 ggml-cpu-android_* 变体，未编入 Vulkan/Metal 等
 * 加速后端；因此 GPU/NPU [native backend] 不存在，仅当 [nativeAvailable]
 * （LlamaNative 库加载成功）时为 CPU。未来接入加速后端时在此扩展并同步
 * llama_jni.cpp，切勿在无对应原生路径时声称支持。
 */
internal fun supportedInferenceBackends(nativeAvailable: Boolean): Set<InferenceBackend> =
    if (nativeAvailable) setOf(InferenceBackend.CPU) else emptySet()

/**
 * 后端选择与回退（纯函数，可 JVM 单测）：
 * 1. 选中后端受支持 → 原样返回（不篡改选择）；
 * 2. 选中后端不受支持（如 GPU/NPU 无 native backend）→ 显式回退 CPU，
 *    不得伪造加速；
 * 3. 连 CPU 也不可用（llama 运行时缺失）→ 返回 null，表示无本地推理后端。
 */
internal fun effectiveInferenceBackend(
    selected: InferenceBackend,
    supported: Set<InferenceBackend>,
): InferenceBackend? = when {
    selected in supported -> selected
    InferenceBackend.CPU in supported -> InferenceBackend.CPU
    else -> null
}

private const val INFERENCE_PREFS = "inference_engine"
private const val BACKEND_KEY = "backend"

internal fun selectedInferenceBackend(context: Context): InferenceBackend =
    InferenceBackend.fromWireName(
        context.getSharedPreferences(INFERENCE_PREFS, Context.MODE_PRIVATE)
            .getString(BACKEND_KEY, InferenceBackend.CPU.wireName),
    )

internal fun setInferenceBackend(context: Context, backend: InferenceBackend) {
    context.getSharedPreferences(INFERENCE_PREFS, Context.MODE_PRIVATE)
        .edit().putString(BACKEND_KEY, backend.wireName).apply()
}
