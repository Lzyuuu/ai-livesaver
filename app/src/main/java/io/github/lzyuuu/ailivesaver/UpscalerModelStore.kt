package io.github.lzyuuu.ailivesaver

import android.content.Context
import java.io.File

/**
 * HD Upscaler 模型包（阶段③，商店条目 hd-upscalers / Enhance HD）：
 * Aura 设备端 Real-ESRGAN x4 双模型（照片 + 画稿），MNN fp16。
 * 源 Mr-J-369/Fancy-AI（hf-mirror.com 镜像；huggingface.co 直连不可达）。
 * 合计 42,638,892 B 与商店 requiredDownloadBytes 一致；SHA-256 为镜像下载实测值。
 */
internal data class UpscalerModelEntry(
    val id: String,
    val name: String,
    val file: String,
    val bytes: Long,
    val sha256: String,
)

internal object UpscalerModelStore {
    internal const val URL_BASE =
        "https://hf-mirror.com/Mr-J-369/Fancy-AI/resolve/main"
    private const val PREFS = "upscaler_model_store"
    private const val DIR_NAME = "upscalers"

    val catalog: List<UpscalerModelEntry> = listOf(
        UpscalerModelEntry(
            id = "upscaler-photo",
            name = "照片放大 4×（Real-ESRGAN x4plus）",
            file = "realesrgan_x4plus.fp16.mnn",
            bytes = 33637604L,
            sha256 = "4897fc77dac1bb786b9439c14cd14e75bff401d7397f67cad14c0935738fa47c",
        ),
        UpscalerModelEntry(
            id = "upscaler-anime",
            name = "画稿放大 4×（Real-ESRGAN anime）",
            file = "realesrgan_x4plus_anime.fp16.mnn",
            bytes = 9001288L,
            sha256 = "a2ac13bd46fd3e21e9ef2e83832b659267bd0f54c0db8f2360332a257305b69c",
        ),
    )

    const val TOTAL_BYTES: Long = 42638892L

    fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun modelFile(context: Context, entry: UpscalerModelEntry): File? {
        val file = File(dir(context), entry.file)
        return if (file.isFile && HfModelStore.matchesTrustedSha(file, entry.sha256)) file else null
    }

    fun isReady(context: Context, entry: UpscalerModelEntry): Boolean =
        modelFile(context, entry) != null

    fun allReady(context: Context): Boolean = catalog.all { isReady(context, it) }

    fun readyBytes(context: Context): Long =
        catalog.filter { isReady(context, it) }.sumOf { it.bytes }

    fun remove(context: Context) {
        dir(context).deleteRecursively()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        catalog.forEach { prefs.edit().remove("v_" + it.file).apply() }
    }

    fun download(
        context: Context,
        entry: UpscalerModelEntry,
        onProgress: (Long, Long) -> Unit,
        onDone: (Result<Unit>) -> Unit,
    ): Boolean {
        val target = dir(context)
        Thread {
            val result = runCatching {
                onProgress(0L, entry.bytes)
                val file = HfModelStore.downloadFile(target, "$URL_BASE/${entry.file}", entry.file, entry.sha256)
                if (!HfModelStore.matchesTrustedSha(file, entry.sha256)) error("校验失败：${entry.file}")
                onProgress(entry.bytes, entry.bytes)
            }
            result.onFailure { android.util.Log.e("UpscalerModelStore", "download ${entry.id} failed", it) }
            onDone(result)
        }.start()
        return true
    }
}
