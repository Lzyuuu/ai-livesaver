package com.mrj.fancyai.sd

import android.graphics.Bitmap
import com.mrj.fancyai.FancyApplication

/**
 * SD 1.5 本机生图 JNI（阶段② #73）。
 * 符号来自参考 V4.51 `libfancy_mnn_diffusion.so`：
 * nativeLoad(FancyApplication, dir) 与
 * nativeGenerate(..., sampler, schedule, vPred, output, NativeImageProgress)。
 */
object MnnSd15Diffusion {
    const val ABI_VERSION = 2

    init {
        runCatching {
            System.loadLibrary("MNN")
            System.loadLibrary("fancy_mnn_diffusion")
        }
    }

    @JvmStatic external fun nativeAbiVersion(): Int

    @JvmStatic external fun nativeLoad(application: FancyApplication, dir: String): Boolean

    @JvmStatic external fun nativeSetMemoryPolicy(policy: Int)

    @JvmStatic external fun nativeFinishRequest()

    @JvmStatic external fun nativeUnload()

    @JvmStatic external fun nativeCancel()

    @JvmStatic external fun nativeWasCancelled(): Boolean

    @JvmStatic external fun nativeGenerate(
        prompt: String,
        negativePrompt: String,
        steps: Int,
        cfg: Float,
        seed: Long,
        source: Bitmap?,
        strength: Float,
        sampler: Int,
        schedule: Int,
        vPred: Boolean,
        output: Bitmap,
        progress: NativeImageProgress,
    ): Boolean
}

fun interface NativeImageProgress {
    fun onProgress(percent: Int)
}
