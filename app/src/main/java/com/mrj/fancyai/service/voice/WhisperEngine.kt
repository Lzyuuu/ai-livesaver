package com.mrj.fancyai.service.voice

import com.mrj.fancyai.FancyApplication

/**
 * libfancy_whisper.so（whisper.cpp）绑定，符号与参考 V4.51 的
 * com.mrj.fancyai.service.voice.WhisperEngine 一致（阶段② SE-05 ASR）。
 * 模型：ggerganov/whisper.cpp 的 ggml-*.bin（fancy/manifest.json asr 条目）。
 */
object WhisperEngine {
    const val ABI_VERSION = 1

    private var libLoaded = false

    init {
        runCatching {
            System.loadLibrary("ggml-base")
            System.loadLibrary("ggml")
            System.loadLibrary("fancy_whisper")
            libLoaded = nativeAbiVersion() == ABI_VERSION
            android.util.Log.i("WhisperEngine", "loaded ok, abi=${runCatching { nativeAbiVersion() }.getOrDefault(-1)}")
        }.onFailure {
            android.util.Log.e("WhisperEngine", "whisper lib load failed", it)
        }
    }

    val available: Boolean
        get() = libLoaded

    @JvmStatic external fun nativeAbiVersion(): Int

    @JvmStatic external fun nativeWhisperLoaded(): Boolean

    @JvmStatic external fun nativeWhisperFree()

    @JvmStatic external fun nativeWhisperTranscribe(pcm: FloatArray, threads: Int, lang: String): String

    private external fun nativeWhisperInit(application: FancyApplication, path: String): Boolean

    @Synchronized
    fun init(application: FancyApplication, modelPath: String): Boolean {
        if (!libLoaded) return false
        return runCatching { nativeWhisperInit(application, modelPath) }.getOrDefault(false)
    }

    /** pcm：16kHz 单声道 float。threads 0 = 自动；lang "" = 自动检测。 */
    fun transcribe(pcm: FloatArray, threads: Int = 2, lang: String = ""): String {
        if (!available || !nativeWhisperLoaded()) return ""
        return runCatching { nativeWhisperTranscribe(pcm, threads, lang) }.getOrDefault("")
    }

    @Synchronized
    fun free() {
        if (libLoaded) runCatching { nativeWhisperFree() }
    }
}
