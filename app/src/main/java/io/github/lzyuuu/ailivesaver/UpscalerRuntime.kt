package io.github.lzyuuu.ailivesaver

import android.content.Context
import android.graphics.Bitmap
import com.mrj.fancyai.FancyApplication
import com.mrj.fancyai.sd.MnnUpscaler
import java.io.File

/**
 * HD Upscaler 运行时（阶段③）：Real-ESRGAN x4（MNN）放大。
 * tile 参数沿用参考 V4.51（tileSide=304）；useGpu=false 保证模拟器/低端机可跑。
 */
internal object UpscalerRuntime {
    private var loadedEntryId: String? = null

    @Synchronized
    fun ensureModel(context: Context, entry: UpscalerModelEntry): Boolean {
        if (!MnnUpscaler.available) {
            android.util.Log.e("UpscalerRuntime", "ensureModel: lib unavailable")
            return false
        }
        if (loadedEntryId == entry.id) return true
        val app = context.applicationContext as? FancyApplication ?: FancyApplication.u
        if (app == null) {
            android.util.Log.e("UpscalerRuntime", "ensureModel: no FancyApplication")
            return false
        }
        val file = UpscalerModelStore.modelFile(context, entry)
        if (file == null) {
            android.util.Log.e("UpscalerRuntime", "ensureModel: model file missing/sha ${entry.id}")
            return false
        }
        return runCatching {
            val ok = MnnUpscaler.init(app, file.absolutePath, useGpu = false)
            android.util.Log.e("UpscalerRuntime", "init ${entry.id} ok=$ok scale=${if (ok) MnnUpscaler.nativeScale() else -1}")
            if (ok) loadedEntryId = entry.id
            ok
        }.onFailure {
            android.util.Log.e("UpscalerRuntime", "init failed", it)
        }.getOrDefault(false)
    }

    /** 放大 4×；返回新 Bitmap（ARGB_8888），失败返回 null。 */
    @Synchronized
    fun upscale(context: Context, entry: UpscalerModelEntry, source: Bitmap): Bitmap? {
        if (!ensureModel(context, entry)) return null
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        return runCatching {
            // 参考调用方实参：coreTile=256、pad=24（wf2.java 反编译实证）。
            val out = MnnUpscaler.nativeUpscaleImage(pixels, width, height, 256, 24)
            android.util.Log.e("UpscalerRuntime", "upscale ${width}x${height} -> ${out.size} px")
            if (out.isEmpty()) return null
            val scale = MnnUpscaler.nativeScale().takeIf { it > 0 } ?: 4
            Bitmap.createBitmap(out, width * scale, height * scale, Bitmap.Config.ARGB_8888)
        }.onFailure {
            android.util.Log.e("UpscalerRuntime", "nativeUpscaleImage failed", it)
        }.getOrNull()
    }

    /** 对媒体文件执行 4× 放大并另存 PNG，返回新文件路径。 */
    fun upscaleFile(context: Context, entry: UpscalerModelEntry, source: File): Pair<Bitmap, File>? {
        val bitmap = android.graphics.BitmapFactory.decodeFile(source.absolutePath) ?: return null
        val up = upscale(context, entry, bitmap) ?: return null
        val out = File(File(context.filesDir, "media").apply { mkdirs() }, "hd-${System.currentTimeMillis()}.png")
        return runCatching {
            out.outputStream().use { up.compress(Bitmap.CompressFormat.PNG, 100, it) }
            up to out
        }.getOrNull()
    }
}
