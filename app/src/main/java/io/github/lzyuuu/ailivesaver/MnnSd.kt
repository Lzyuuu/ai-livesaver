package io.github.lzyuuu.ailivesaver

import android.content.Context
import android.graphics.Bitmap
import com.mrj.fancyai.FancyApplication
import com.mrj.fancyai.sd.MnnSd15Diffusion
import java.io.File

/**
 * CyberRealistic-LCM 本地生图桥（阶段② #73）。
 * 对接参考 V4.51 `libfancy_mnn_diffusion.so`：模型目录
 * `models/CyberRealistic-LCM/`，分辨率固定 512×512。
 */
internal object MnnSd {
    private const val TAG = "MnnSd"

    /** 参考 i35.LCM ordinal。 */
    const val SAMPLER_LCM = 12
    /** 参考 u45.KARRAS。 */
    const val SCHEDULE_KARRAS = 1
    /** 参考 js3.LOW_MEMORY。 */
    const val MEMORY_LOW = 0
    const val REQUIRED_SIZE = 512

    private var loaded = false

    fun modelDir(context: Context): File = ImagingModelStore.dir(context)

    @Synchronized
    fun ensureLoaded(context: Context): Boolean {
        if (loaded) return true
        if (!ImagingModelStore.isReady(context)) {
            android.util.Log.e(TAG, "ensureLoaded: model files not ready")
            return false
        }
        val app = context.applicationContext as? FancyApplication ?: FancyApplication.u
        if (app == null) {
            android.util.Log.e(TAG, "ensureLoaded: no FancyApplication instance")
            return false
        }
        return runCatching {
            val abi = MnnSd15Diffusion.nativeAbiVersion()
            android.util.Log.e(TAG, "ensureLoaded abi=$abi")
            if (abi != MnnSd15Diffusion.ABI_VERSION) {
                return false
            }
            val dir = modelDir(context).absolutePath
            val ok = MnnSd15Diffusion.nativeLoad(app, dir)
            android.util.Log.e(TAG, "nativeLoad=$ok dir=$dir")
            if (ok) {
                MnnSd15Diffusion.nativeSetMemoryPolicy(MEMORY_LOW)
            }
            loaded = ok
            ok
        }.getOrElse {
            android.util.Log.e(TAG, "ensureLoaded failed", it)
            false
        }
    }

    @Synchronized
    fun unload() {
        if (!loaded) return
        runCatching { MnnSd15Diffusion.nativeUnload() }
        loaded = false
    }

    /**
     * 文生图（LCM：steps 4–8、cfg 1.0、512×512）。
     * [onProgress] 收到 native 百分比 0..100；失败返回 null。
     */
    fun generate(
        context: Context,
        prompt: String,
        negative: String,
        steps: Int,
        guidance: Float,
        seed: Long,
        width: Int = REQUIRED_SIZE,
        height: Int = REQUIRED_SIZE,
        onProgress: (Int) -> Unit = {},
    ): Bitmap? {
        if (width != REQUIRED_SIZE || height != REQUIRED_SIZE) return null
        if (!ensureLoaded(context)) return null
        val output = Bitmap.createBitmap(REQUIRED_SIZE, REQUIRED_SIZE, Bitmap.Config.ARGB_8888)
        return runCatching {
            val ok = MnnSd15Diffusion.nativeGenerate(
                prompt = prompt,
                negativePrompt = negative,
                steps = steps,
                cfg = guidance,
                seed = seed,
                source = null,
                strength = 1.0f,
                sampler = SAMPLER_LCM,
                schedule = SCHEDULE_KARRAS,
                vPred = false,
                output = output,
                progress = { percent -> onProgress(percent.coerceIn(0, 100)) },
            )
            android.util.Log.e(TAG, "nativeGenerate=$ok steps=$steps seed=$seed cancelled=${MnnSd15Diffusion.nativeWasCancelled()}")
            if (!ok) {
                output.recycle()
                null
            } else {
                runCatching { MnnSd15Diffusion.nativeFinishRequest() }
                output
            }
        }.getOrElse {
            if (!output.isRecycled) output.recycle()
            null
        }
    }
}
