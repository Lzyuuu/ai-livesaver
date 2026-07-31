package io.github.lzyuuu.ailivesaver

internal object MnnNative {
    init {
        System.loadLibrary("aura_mnn_jni")
    }

    external fun nativeLoadModels(detector: String, embedding: String, swapper: String): String
}
