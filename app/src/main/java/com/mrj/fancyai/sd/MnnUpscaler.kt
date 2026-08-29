package com.mrj.fancyai.sd

import com.mrj.fancyai.FancyApplication

/**
 * libfancy_mnn_upscale.so（Real-ESRGAN x4）绑定，符号与参考 V4.51 的
 * com.mrj.fancyai.sd.MnnUpscaler 一致（阶段③ HD Upscaler）。
 * 参考参数：nativeLoad(app, modelPath, useGpu=true, tileSide=304)、ABI=1；
 * nativeUpscaleImage(argb[], w, h, coreTile, pad) 返回放大后的 argb 像素。
 */
object MnnUpscaler {
    const val ABI_VERSION = 1
    const val TILE_SIDE = 304

    private var libLoaded = false

    init {
        runCatching {
            System.loadLibrary("MNN")
            System.loadLibrary("fancy_mnn_upscale")
            libLoaded = nativeAbiVersion() == ABI_VERSION
        }
    }

    val available: Boolean
        get() = libLoaded

    @JvmStatic external fun nativeAbiVersion(): Int

    private external fun nativeLoad(application: FancyApplication, modelPath: String, useGpu: Boolean, tileSide: Int): Boolean

    @JvmStatic external fun nativeScale(): Int

    @JvmStatic external fun nativeUnload()

    @JvmStatic external fun nativeUpscaleImage(argb: IntArray, width: Int, height: Int, coreTile: Int, pad: Int): IntArray

    @Synchronized
    fun init(application: FancyApplication, modelPath: String, useGpu: Boolean = false): Boolean {
        if (!libLoaded) return false
        return runCatching { nativeLoad(application, modelPath, useGpu, TILE_SIDE) }.getOrDefault(false)
    }

    @Synchronized
    fun release() {
        if (libLoaded) runCatching { nativeUnload() }
    }
}
