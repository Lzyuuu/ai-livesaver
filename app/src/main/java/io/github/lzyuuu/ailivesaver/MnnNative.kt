package io.github.lzyuuu.ailivesaver

internal object MnnNative {
    init {
        System.loadLibrary("fancysd")
        System.loadLibrary("aura_mnn_jni")
    }

    external fun nativeLoad(detectPath: String, embedPath: String, swapPath: String, restorePath: String?, useGpu: Boolean): String
    external fun nativeDetect(inputChw640: FloatArray): FloatArray
    external fun nativeEmbed(inputChw112: FloatArray): FloatArray
    external fun nativeSwap(inputChw128: FloatArray, embedding: FloatArray): FloatArray
    external fun nativeRestore(inputChw512: FloatArray, fidelity: Float): FloatArray
    external fun nativeUnload()
    external fun nativeLoadModels(detector: String, embedding: String, swapper: String): String
}
